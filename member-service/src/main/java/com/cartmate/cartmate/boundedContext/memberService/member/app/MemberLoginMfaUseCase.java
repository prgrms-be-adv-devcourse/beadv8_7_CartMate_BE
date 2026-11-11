package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.EmailVerificationProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MfaProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.LoginOutcome;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.LoginResult;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.MfaChallengeResponse;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.ConsumeResult;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.IssueResult;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationMailSender;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * 로그인 2차 인증 (일반 회원 필수, 이메일 코드).
 *
 * <pre>
 * 비밀번호 맞음 → 신뢰 기기면 바로 토큰 (기기 토큰 교체)
 *              → 아니면 토큰 없이 challengeId + 이메일로 6자리
 * 코드 맞음    → challenge를 지운 요청 하나만 토큰 (+ "30일 기억"이면 신뢰 기기 등록)
 * </pre>
 *
 * <p>challenge는 인증번호와 같은 Redis 해시 하나에 둔다 (회원 ID · 로그인 유지 여부를 함께). 코드 확인과 challenge 삭제가
 * 한 스크립트라 동시에 맞혀도 하나만 성공한다. 틀린 횟수는 challenge당 5번, 계정당 1시간 10번 (새로 로그인해 우회 방지).
 * 관리자는 신뢰 기기로 생략하지 않는다 (관리자 OTP는 다음 단계에서 이 자리를 바꾼다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(MfaProperties.class)
class MemberLoginMfaUseCase {

    private static final String CODE_PURPOSE = "login-mfa:";
    private static final String FAILS = "login-mfa-fails:";
    private static final String MEMBER_ID = "memberId";
    private static final String REMEMBER = "remember";
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final MemberSupport memberSupport;
    private final MemberTokenUseCase memberTokenUseCase;
    private final TrustedDeviceUseCase trustedDeviceUseCase;
    private final VerificationCodeStore codeStore;
    private final VerificationCodeHasher hasher;
    private final VerificationMailSender mailSender;
    private final EmailVerificationProperties codeRules;
    private final MfaProperties properties;

    // 비밀번호를 맞힌 회원: 2차 인증이 필요한지 정하고, 필요하면 코드를 보낸다. 트랜잭션 밖 (메일 발송 동안 DB 연결을 잡지 않음)
    LoginOutcome afterPassword(Member member, boolean remember, String trustedDeviceToken, Instant now) {
        boolean admin = member.getRole() == MemberRole.ADMIN;
        if (!admin && !properties.required()) {
            return LoginOutcome.loggedIn(issueTokens(member, remember), null);
        }
        if (!admin) {
            Optional<String> rotated = trustedDeviceUseCase.useForLogin(member.getId(), trustedDeviceToken, local(now));
            if (rotated.isPresent()) {
                log.info("[로그인 2차 인증] 신뢰 기기로 생략: 회원 #{}", member.getId());
                return LoginOutcome.loggedIn(issueTokens(member, remember), rotated.get());
            }
        }
        rejectIfLocked(member.getId());

        String challengeId = SecureTokens.newToken();
        sendCode(challengeId, member.getEmail(), now,
                Map.of(MEMBER_ID, member.getId().toString(), REMEMBER, String.valueOf(remember)));
        log.info("[로그인 2차 인증] 코드 발송: 회원 #{}", member.getId());
        return LoginOutcome.challenged(new MfaChallengeResponse(challengeId,
                MemberEmailVerificationUseCase.mask(member.getEmail()), (int) codeRules.codeTtl().toSeconds()));
    }

    // 트랜잭션 안에서 부른다 (이메일 확인 기록, 토큰·기기 저장)
    LoginOutcome verify(String challengeId, String code, boolean trustDevice, String deviceName, Instant now) {
        String key = CODE_PURPOSE + challengeId;
        ConsumeResult result = codeStore.consume(key, hasher.hash(key, code), now, codeRules.maxAttempts(),
                MEMBER_ID, REMEMBER);
        String memberIdValue = result.fields().get(MEMBER_ID);
        if (memberIdValue == null) {
            throw expired(); // 없는 challenge (만료, 이미 성공, 다시 로그인 필요)
        }
        Long memberId = Long.valueOf(memberIdValue);
        switch (result.status()) {
            case VERIFIED -> {
            }
            case NOT_SENT, EXPIRED -> throw expired();
            case TOO_MANY_ATTEMPTS -> throw new ServiceException("429-MEMBER_MFA_TOO_MANY_ATTEMPTS",
                    "인증 시도 횟수를 초과했습니다. 다시 로그인해주세요.");
            case MISMATCH -> {
                long fails = codeStore.increaseCount(FAILS + memberId, properties.accountFailWindow());
                log.info("[로그인 2차 인증] 불일치: 회원 #{} (계정 {}/{})", memberId, fails, properties.accountFailLimit());
                if (fails >= properties.accountFailLimit()) {
                    throw locked();
                }
                throw new ServiceException("400-MEMBER_MFA_CODE_MISMATCH", "인증번호가 일치하지 않습니다. ("
                        + Math.max(0, codeRules.maxAttempts() - result.attempts()) + "번 남음)");
            }
        }
        rejectIfLocked(memberId); // 계정이 잠겼으면 맞혀도 거절 (challenge는 이미 지워짐 → 다시 로그인)

        LocalDateTime nowLocal = local(now);
        Member member = memberSupport.findById(memberId).orElseThrow(MemberLoginMfaUseCase::expired);
        member.validateCanLogin();
        member.markEmailVerified(nowLocal); // 기존 회원은 여기서 이메일 확인도 끝난다
        LoginResult login = issueTokens(member, Boolean.parseBoolean(result.fields().get(REMEMBER)));
        String deviceToken = trustDevice && member.getRole() != MemberRole.ADMIN
                ? trustedDeviceUseCase.register(memberId, deviceName, nowLocal)
                : null;
        log.info("[로그인 2차 인증] 성공: 회원 #{} (기기 기억 {})", memberId, deviceToken != null);
        return LoginOutcome.loggedIn(login, deviceToken);
    }

    // 트랜잭션 밖 (메일 발송)
    int resend(String challengeId, Instant now) {
        Long memberId = codeStore.field(CODE_PURPOSE + challengeId, MEMBER_ID).map(Long::valueOf)
                .orElseThrow(MemberLoginMfaUseCase::expired);
        Member member = memberSupport.findById(memberId).orElseThrow(MemberLoginMfaUseCase::expired);
        sendCode(challengeId, member.getEmail(), now, Map.of());
        return (int) codeRules.codeTtl().toSeconds();
    }

    private void sendCode(String challengeId, String email, Instant now, Map<String, String> challengeFields) {
        String key = CODE_PURPOSE + challengeId;
        String code = hasher.newCode();
        IssueResult issued = codeStore.issue(key, hasher.hash(key, code), now, codeRules.codeTtl(),
                codeRules.resendCooldown(), codeRules.maxSends(), codeRules.maxAttempts(), properties.challengeTtl(),
                challengeFields);
        switch (issued.status()) {
            case TOO_SOON -> throw new ServiceException("429-MEMBER_MFA_RESEND_TOO_SOON",
                    (issued.retryAfter().toSeconds() + 1) + "초 후에 다시 요청해주세요.");
            case TOO_MANY_SENDS -> throw new ServiceException("429-MEMBER_MFA_TOO_MANY_SENDS",
                    "인증 메일을 너무 많이 요청했습니다. 다시 로그인해주세요.");
            case TOO_MANY_ATTEMPTS -> throw new ServiceException("429-MEMBER_MFA_TOO_MANY_ATTEMPTS",
                    "인증 시도 횟수를 초과했습니다. 다시 로그인해주세요.");
            case ISSUED -> {
            }
        }
        try {
            mailSender.send(email, "[CartMate] 로그인 인증번호",
                    "로그인 인증번호는 " + code + " 입니다.\n" + MemberEmailVerificationUseCase.readable(codeRules.codeTtl())
                            + " 안에 입력해주세요.\n본인이 로그인하지 않았다면 비밀번호를 바꿔 주세요.");
        } catch (MailException e) {
            codeStore.releaseCooldown(key);
            log.warn("[로그인 2차 인증] 메일 발송 실패: {}", MemberEmailVerificationUseCase.mask(email), e);
            throw new ServiceException("503-MEMBER_MFA_SEND_FAILED", "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private LoginResult issueTokens(Member member, boolean remember) {
        return new LoginResult(member.toDto(), memberTokenUseCase.issue(member, remember));
    }

    private void rejectIfLocked(Long memberId) {
        if (codeStore.count(FAILS + memberId) >= properties.accountFailLimit()) {
            throw locked();
        }
    }

    private static LocalDateTime local(Instant now) {
        return LocalDateTime.ofInstant(now, ZONE);
    }

    private static ServiceException locked() {
        return new ServiceException("429-MEMBER_MFA_LOCKED", "인증 실패가 많아 잠시 로그인할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    private static ServiceException expired() {
        return new ServiceException("400-MEMBER_MFA_EXPIRED", "인증 시간이 지났습니다. 다시 로그인해주세요.");
    }
}
