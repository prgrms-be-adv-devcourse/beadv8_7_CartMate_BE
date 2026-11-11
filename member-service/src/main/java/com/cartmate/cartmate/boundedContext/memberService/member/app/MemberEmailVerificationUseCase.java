package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.EmailVerificationProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.IssueResult;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.VerifyResult;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationMailSender;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * 가입 이메일 확인: 이메일로 6자리 코드를 보내고, 맞히면 일정 시간 "확인됨"으로 둔다 (그 안에 가입을 마쳐야 한다).
 *
 * <ul>
 *   <li>코드는 HMAC 해시로만 저장, 5분 유효</li>
 *   <li>재발송은 60초 뒤부터, 최대 5번. 재발송하면 이전 코드는 바로 무효, 틀린 횟수는 그대로</li>
 *   <li>5번 틀리면 막힌다. 첫 발송부터 30분이 지나야 처음부터 다시 할 수 있다</li>
 * </ul>
 * 트랜잭션 밖에서 부른다: 메일 발송(SMTP) 동안 DB 연결을 잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class MemberEmailVerificationUseCase {

    private static final String PURPOSE = "signup-email:";

    private final VerificationCodeStore store;
    private final VerificationCodeHasher hasher;
    private final VerificationMailSender mailSender;
    private final MemberSupport memberSupport;
    private final EmailVerificationProperties properties;

    // 코드 보내기. 돌려주는 값: 코드 유효 시간(초)
    public int send(String rawEmail, Instant now) {
        String email = normalize(rawEmail);
        if (memberSupport.existsByEmail(email)) {
            throw new ServiceException("409-MEMBER_DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
        }
        String key = PURPOSE + email;
        String code = hasher.newCode();
        IssueResult issued = store.issue(key, hasher.hash(key, code), now, properties.codeTtl(),
                properties.resendCooldown(), properties.maxSends(), properties.maxAttempts(), properties.sendWindow());
        switch (issued.status()) {
            case TOO_SOON -> throw new ServiceException("429-MEMBER_EMAIL_RESEND_TOO_SOON",
                    (issued.retryAfter().toSeconds() + 1) + "초 후에 다시 요청해주세요.");
            case TOO_MANY_SENDS -> throw new ServiceException("429-MEMBER_EMAIL_TOO_MANY_SENDS",
                    "인증 메일을 너무 많이 요청했습니다. 잠시 후 다시 시도해주세요.");
            case TOO_MANY_ATTEMPTS -> throw tooManyAttempts();
            case ISSUED -> {
            }
        }

        try {
            mailSender.send(email, "[CartMate] 이메일 인증번호",
                    "인증번호는 " + code + " 입니다.\n" + readable(properties.codeTtl()) + " 안에 입력해주세요.\n"
                            + "본인이 요청하지 않았다면 이 메일을 무시해주세요.");
        } catch (MailException e) {
            store.releaseCooldown(key);
            log.warn("[이메일 인증] 메일 발송 실패: {}", mask(email), e);
            throw new ServiceException("503-MEMBER_EMAIL_SEND_FAILED", "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        log.info("[이메일 인증] 발송: {}", mask(email));
        return (int) properties.codeTtl().toSeconds();
    }

    public void verify(String rawEmail, String code, Instant now) {
        String email = normalize(rawEmail);
        String key = PURPOSE + email;
        VerifyResult result = store.verify(key, hasher.hash(key, code), now,
                properties.maxAttempts(), properties.verifiedTtl());
        switch (result.status()) {
            case VERIFIED -> log.info("[이메일 인증] 확인: {}", mask(email));
            case NOT_SENT -> throw new ServiceException("400-MEMBER_EMAIL_CODE_NOT_SENT", "인증번호를 먼저 요청해주세요.");
            case TOO_MANY_ATTEMPTS -> throw tooManyAttempts();
            case EXPIRED -> throw new ServiceException("400-MEMBER_EMAIL_CODE_EXPIRED", "인증번호가 만료되었습니다. 다시 요청해주세요.");
            case MISMATCH -> {
                log.info("[이메일 인증] 불일치: {} ({}/{})", mask(email), result.attempts(), properties.maxAttempts());
                throw new ServiceException("400-MEMBER_EMAIL_CODE_MISMATCH", "인증번호가 일치하지 않습니다. ("
                        + Math.max(0, properties.maxAttempts() - result.attempts()) + "번 남음)");
            }
        }
    }

    public boolean isVerified(String rawEmail, Instant now) {
        return store.isVerified(PURPOSE + normalize(rawEmail), now);
    }

    // 가입이 끝나면 확인 기록을 지운다 (같은 확인으로 두 번 가입하지 못하게)
    public void consume(String rawEmail) {
        store.remove(PURPOSE + normalize(rawEmail));
    }

    private static ServiceException tooManyAttempts() {
        return new ServiceException("429-MEMBER_EMAIL_TOO_MANY_ATTEMPTS", "인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    // 로그에는 이메일을 가린다: buyer@cartmate.com → bu***@cartmate.com
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    // 메일에 보일 유효 시간: 분으로 딱 떨어지면 "5분", 아니면 "90초"
    static String readable(Duration duration) {
        long seconds = duration.toSeconds();
        return seconds % 60 == 0 ? (seconds / 60) + "분" : seconds + "초";
    }
}
