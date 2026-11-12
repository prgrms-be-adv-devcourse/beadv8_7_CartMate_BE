package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
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
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 로그인 2차 인증 (일반 회원: 이메일 코드, 관리자: OTP).
 *
 * <pre>
 * 비밀번호 맞음 → 일반 회원: 신뢰 기기면 바로 토큰 (기기 토큰 교체), 아니면 토큰 없이 challengeId + 이메일로 6자리
 *              → 관리자 + OTP 등록됨: 토큰 없이 challengeId (메일 없음, OTP 앱 코드 또는 백업 코드)
 *              → 관리자 + OTP 없음: 이메일 코드 → OTP 등록 전용 세션 (관리자 API 403)
 * 코드 맞음    → challenge를 지운 요청 하나만 토큰 (+ "30일 기억"이면 신뢰 기기 등록, 관리자는 안 함)
 * </pre>
 *
 * <p>이메일 challenge는 인증번호와 같은 Redis 해시 하나에 둔다 (회원 ID · 로그인 유지 여부를 함께). 코드 확인과 challenge 삭제가
 * 한 스크립트라 동시에 맞혀도 하나만 성공한다. 틀린 횟수는 challenge당 5번, 계정당 1시간 10번 (새로 로그인해 우회 방지).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(MfaProperties.class)
class MemberLoginMfaUseCase {

    private static final String CODE_PURPOSE = "login-mfa:";
    private static final String OTP_PURPOSE = "login-otp:";
    // 관리자 OTP challenge는 ID 앞에 붙여 구분한다 → 확인·재발송 때 Redis를 읽지 않고 방식을 안다 (없는 ID면 만료로 끝)
    private static final String OTP_CHALLENGE_PREFIX = "otp.";
    private static final String FAILS = "login-mfa-fails:";
    private static final String MEMBER_ID = "memberId";
    private static final String REMEMBER = "remember";
    private static final String AMR_EMAIL = "email";
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final MemberSupport memberSupport;
    private final MemberTokenUseCase memberTokenUseCase;
    private final TrustedDeviceUseCase trustedDeviceUseCase;
    private final VerificationCodeStore codeStore;
    private final VerificationCodeHasher hasher;
    private final VerificationMailSender mailSender;
    private final EmailVerificationProperties codeRules;
    private final MfaProperties properties;
    private final AdminOtpUseCase adminOtpUseCase;

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

        // 관리자이고 OTP가 등록돼 있으면 OTP 앱 코드로만 (이메일 경로 없음). 방식은 요청이 아니라 DB 역할·상태로 정한다
        if (admin && adminOtpUseCase.isEnabled(member.getId())) {
            String challengeId = OTP_CHALLENGE_PREFIX + SecureTokens.newToken();
            codeStore.create(OTP_PURPOSE + challengeId, Map.of(MEMBER_ID, member.getId().toString()),
                    properties.challengeTtl());
            log.info("[로그인 2차 인증] 관리자 OTP 요청: 회원 #{}", member.getId());
            return LoginOutcome.challenged(new MfaChallengeResponse(challengeId, MfaChallengeResponse.OTP, null,
                    (int) properties.challengeTtl().toSeconds()));
        }
        // 일반 회원, 또는 OTP를 아직 등록하지 않은 관리자(이메일로 본인 확인 → OTP 등록 전용 세션)
        String challengeId = SecureTokens.newToken();
        sendCode(challengeId, member.getEmail(), now,
                Map.of(MEMBER_ID, member.getId().toString(), REMEMBER, String.valueOf(remember)));
        log.info("[로그인 2차 인증] 코드 발송: 회원 #{}", member.getId());
        return LoginOutcome.challenged(new MfaChallengeResponse(challengeId, MfaChallengeResponse.EMAIL,
                MemberEmailVerificationUseCase.mask(member.getEmail()), (int) codeRules.codeTtl().toSeconds()));
    }

    // 트랜잭션 안에서 부른다 (이메일 확인 기록, 토큰·기기 저장)
    LoginOutcome verify(String challengeId, String code, boolean trustDevice, String deviceName, Instant now) {
        if (challengeId.startsWith(OTP_CHALLENGE_PREFIX)) {
            return verifyAdminOtp(challengeId, code, now);
        }
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
            case TOO_MANY_ATTEMPTS -> throw new ServiceException(MemberErrorCode.MEMBER_MFA_TOO_MANY_ATTEMPTS);
            case MISMATCH -> {
                long fails = codeStore.increaseCount(FAILS + memberId, properties.accountFailWindow());
                log.info("[로그인 2차 인증] 불일치: 회원 #{} (계정 {}/{})", memberId, fails, properties.accountFailLimit());
                if (fails >= properties.accountFailLimit()) {
                    throw locked();
                }
                throw new ServiceException(MemberErrorCode.MEMBER_MFA_CODE_MISMATCH.resultCode(), "인증번호가 일치하지 않습니다. ("
                        + Math.max(0, codeRules.maxAttempts() - result.attempts()) + "번 남음)");
            }
        }
        rejectIfLocked(memberId); // 계정이 잠겼으면 맞혀도 거절 (challenge는 이미 지워짐 → 다시 로그인)

        LocalDateTime nowLocal = local(now);
        Member member = memberSupport.findById(memberId).orElseThrow(MemberLoginMfaUseCase::expired);
        member.validateCanLogin();
        member.markEmailVerified(nowLocal); // 기존 회원은 여기서 이메일 확인도 끝난다
        boolean remember = Boolean.parseBoolean(result.fields().get(REMEMBER));
        LoginResult login = new LoginResult(member.toDto(),
                memberTokenUseCase.issue(member, remember, List.of(MemberTokenUseCase.AMR_PWD, AMR_EMAIL)));
        if (member.getRole() == MemberRole.ADMIN) {
            // OTP 미등록 관리자: 이메일까지 확인했으니 OTP 등록 전용 세션 (amr에 otp 없음 → 관리자 API 403)
            log.info("[보안] 관리자 이메일 확인 → OTP 등록 필요: 회원 #{}", memberId);
            return LoginOutcome.loggedIn(login, null).withOtpEnrollmentRequired();
        }
        String deviceToken = trustDevice ? trustedDeviceUseCase.register(memberId, deviceName, nowLocal) : null;
        log.info("[로그인 2차 인증] 성공: 회원 #{} (기기 기억 {})", memberId, deviceToken != null);
        return LoginOutcome.loggedIn(login, deviceToken);
    }

    // 관리자 OTP 로그인: OTP 앱 코드 또는 백업 코드. 틀리면 challenge·계정 실패 수를 올리고, 맞으면 challenge를 지운 요청 하나만 토큰.
    // Redis: 회원 읽기 · 잠금 확인 · challenge 삭제 (틀리면 실패 수 2번). 관리자는 로그인 유지가 없어 remember를 저장하지 않는다
    private LoginOutcome verifyAdminOtp(String challengeId, String code, Instant now) {
        String key = OTP_PURPOSE + challengeId;
        Long memberId = Optional.ofNullable(codeStore.fields(key, MEMBER_ID).get(MEMBER_ID)).map(Long::valueOf)
                .orElseThrow(MemberLoginMfaUseCase::expired);
        rejectIfLocked(memberId);

        AdminOtpUseCase.LoginCheck check = adminOtpUseCase.verifyForLogin(memberId, code, now);
        if (!check.passed()) {
            long challengeFails = codeStore.increaseCount(key + ":fails", properties.challengeTtl());
            long accountFails = codeStore.increaseCount(FAILS + memberId, properties.accountFailWindow());
            log.info("[로그인 2차 인증] 관리자 OTP 불일치: 회원 #{} (이번 {}/{}, 계정 {}/{})", memberId,
                    challengeFails, codeRules.maxAttempts(), accountFails, properties.accountFailLimit());
            if (accountFails >= properties.accountFailLimit()) {
                codeStore.delete(key);
                throw locked();
            }
            if (challengeFails >= codeRules.maxAttempts()) {
                codeStore.delete(key);
                throw new ServiceException(MemberErrorCode.MEMBER_MFA_TOO_MANY_ATTEMPTS);
            }
            throw new ServiceException(MemberErrorCode.MEMBER_OTP_CODE_MISMATCH.resultCode(), "OTP 코드가 일치하지 않습니다. ("
                    + Math.max(0, codeRules.maxAttempts() - challengeFails) + "번 남음)");
        }
        // 같은 challenge로 동시에 맞혀도 지운 요청 하나만 통과 (진 쪽은 예외 → 트랜잭션 롤백으로 OTP·백업 코드 사용도 되돌림)
        if (!codeStore.delete(key)) {
            throw expired();
        }
        Member member = memberSupport.findById(memberId).orElseThrow(MemberLoginMfaUseCase::expired);
        member.validateCanLogin();
        log.info("[보안] 관리자 OTP 로그인: 회원 #{} ({})", memberId, check.remainingBackupCodes() == null ? "OTP" : "백업 코드");
        LoginResult login = new LoginResult(member.toDto(),
                memberTokenUseCase.issue(member, false, List.of(MemberTokenUseCase.AMR_PWD, SecurityUser.AMR_OTP)));
        return LoginOutcome.loggedIn(login, null).withRemainingBackupCodes(check.remainingBackupCodes());
    }

    // 트랜잭션 밖 (메일 발송)
    int resend(String challengeId, Instant now) {
        if (challengeId.startsWith(OTP_CHALLENGE_PREFIX)) {
            throw new ServiceException(MemberErrorCode.MEMBER_OTP_RESEND_NOT_SUPPORTED); // OTP는 앱에서 30초마다 새로 나온다
        }
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
            case TOO_SOON -> throw new ServiceException(MemberErrorCode.MEMBER_MFA_RESEND_TOO_SOON.resultCode(),
                    (issued.retryAfter().toSeconds() + 1) + "초 후에 다시 요청해주세요.");
            case TOO_MANY_SENDS -> throw new ServiceException(MemberErrorCode.MEMBER_MFA_TOO_MANY_SENDS);
            case TOO_MANY_ATTEMPTS -> throw new ServiceException(MemberErrorCode.MEMBER_MFA_TOO_MANY_ATTEMPTS);
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
            throw new ServiceException(MemberErrorCode.MEMBER_MFA_SEND_FAILED);
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
        return new ServiceException(MemberErrorCode.MEMBER_MFA_LOCKED);
    }

    private static ServiceException expired() {
        return new ServiceException(MemberErrorCode.MEMBER_MFA_EXPIRED);
    }
}
