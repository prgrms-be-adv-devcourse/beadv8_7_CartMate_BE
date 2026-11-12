package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberBackupCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberOtp;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.OtpEnrollmentResponse;
import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberBackupCodeRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberOtpRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * 관리자 OTP: 등록(QR → 첫 코드 확인 → 백업 코드), 로그인 검증(OTP 또는 백업 코드).
 * 등록 시작만 트랜잭션 밖, 나머지는 트랜잭션 안에서 부른다.
 *
 * <p>막아야 하는 우회 경로
 * <ul>
 *   <li>등록은 이메일 인증까지 거친 관리자 세션에서만 시작한다 (비밀번호만 훔친 사람은 등록 불가)</li>
 *   <li>이미 등록된 OTP는 여기서 바꾸거나 지울 수 없다 — Break-glass 절차(운영자 2인, 서버에서 실행)뿐</li>
 *   <li>같은 30초 구간 코드는 한 번만 (markUsed 조건부 UPDATE), 백업 코드는 쓰는 순간 삭제</li>
 *   <li>등록·백업 코드 사용은 커밋된 뒤 메일로 알린다 (본인이 모르는 변화를 알아차리게)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AdminOtpUseCase {

    static final int BACKUP_CODE_COUNT = 10;
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 0·O·1·I 제외, 32자 → 12자 = 60비트
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final MemberSupport memberSupport;
    private final MemberOtpRepository otpRepository;
    private final MemberBackupCodeRepository backupCodeRepository;
    private final OtpSecretCipher cipher;
    private final TotpVerifier totp;
    private final VerificationCodeHasher hasher;
    private final SecurityAlertMailer alertMailer;

    boolean isEnabled(Long memberId) {
        return otpRepository.findByMemberId(memberId).map(MemberOtp::isEnabled).orElse(false);
    }

    /**
     * 등록 시작: 앱에 넣을 주소를 돌려준다. 첫 코드를 맞히기 전엔 로그인에 못 쓴다.
     *
     * <p>여러 번(동시에) 불러도 같은 비밀키: 등록 중인 OTP가 있으면 그 비밀키를 그대로 돌려주고, 없을 때만 새로 만든다.
     * 동시에 처음 저장하면 하나는 유니크 제약(uk_member_otp_member)에 걸린다 → 먼저 저장된 행을 다시 읽어 그 비밀키를 준다.
     * 그래서 <b>트랜잭션 밖에서</b> 부른다 (조회·저장·재조회가 각각 짧은 트랜잭션): 한 트랜잭션이면 제약 위반 뒤
     * 롤백 전용이 되어 커밋이 실패하고, MySQL REPEATABLE READ라 다시 읽어도 상대가 커밋한 행이 안 보인다.
     */
    OtpEnrollmentResponse startEnrollment(Long memberId) {
        Member member = admin(memberId);
        MemberOtp otp = otpRepository.findByMemberId(memberId).orElse(null);
        if (otp == null) {
            otp = createPending(memberId);
        }
        if (otp.isEnabled()) {
            throw new ServiceException(MemberErrorCode.MEMBER_OTP_ALREADY_ENABLED);
        }
        byte[] secret = cipher.decrypt(otp.getSecretEncrypted());
        log.info("[보안] 관리자 OTP 등록 시작: 회원 #{}", memberId);
        return new OtpEnrollmentResponse(totp.otpauthUri(member.getEmail(), secret), totp.base32(secret));
    }

    // 새 비밀키로 등록 중 행을 만든다. 동시에 먼저 만든 요청이 있으면 그 행 (이 요청의 비밀키는 버린다)
    private MemberOtp createPending(Long memberId) {
        try {
            return otpRepository.saveAndFlush(MemberOtp.pending(memberId, cipher.encrypt(totp.newSecret())));
        } catch (DataIntegrityViolationException e) {
            return otpRepository.findByMemberId(memberId).orElseThrow(() -> e);
        }
    }

    // 등록 완료: 첫 코드가 맞아야 활성화. 백업 코드 10개를 만들어 원문을 돌려준다 (이후 다시 볼 수 없음)
    List<String> confirmEnrollment(Long memberId, String code, Instant now) {
        Member member = admin(memberId);
        MemberOtp otp = otpRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ServiceException(MemberErrorCode.MEMBER_OTP_NOT_STARTED));
        if (otp.isEnabled()) {
            throw new ServiceException(MemberErrorCode.MEMBER_OTP_ALREADY_ENABLED);
        }
        OptionalLong step = totp.verify(cipher.decrypt(otp.getSecretEncrypted()), code, now);
        if (step.isEmpty()) {
            throw new ServiceException(MemberErrorCode.MEMBER_OTP_CODE_MISMATCH);
        }
        otp.enable(step.getAsLong(), LocalDateTime.ofInstant(now, ZONE)); // 등록에 쓴 코드는 로그인에 다시 못 쓴다
        List<String> codes = issueBackupCodes(memberId);
        log.atInfo().addKeyValue("event", "auth.otp.enrolled").addKeyValue("memberId", memberId)
                .log("[보안] 관리자 OTP 등록 완료: 회원 #{}", memberId);
        alertMailer.sendAfterCommit(memberId, member.getEmail(), "[CartMate] 관리자 OTP가 등록되었습니다",
                "관리자 계정에 OTP가 등록되었습니다. 본인이 등록하지 않았다면 즉시 운영 책임자에게 알려 주세요.");
        return codes;
    }

    // 로그인: 6자리면 OTP, 아니면 백업 코드
    LoginCheck verifyForLogin(Long memberId, String code, Instant now) {
        MemberOtp otp = otpRepository.findByMemberId(memberId).filter(MemberOtp::isEnabled)
                .orElseThrow(() -> new ServiceException(MemberErrorCode.MEMBER_OTP_NOT_STARTED));
        if (code.matches("\\d{6}")) {
            OptionalLong step = totp.verify(cipher.decrypt(otp.getSecretEncrypted()), code, now);
            // 같은 구간(또는 이전) 코드를 다시 쓰면 markUsed가 0 → 실패로 본다 (엿본 코드 재사용·동시 사용 차단)
            boolean ok = step.isPresent() && otpRepository.markUsed(memberId, step.getAsLong()) == 1;
            return ok ? LoginCheck.passedByOtp() : LoginCheck.failed();
        }
        if (backupCodeRepository.use(memberId, backupHash(memberId, code)) != 1) {
            return LoginCheck.failed();
        }
        int remaining = (int) backupCodeRepository.countByMemberId(memberId);
        log.atWarn().addKeyValue("event", "auth.otp.backup_used").addKeyValue("memberId", memberId)
                .addKeyValue("remaining", remaining)
                .log("[보안] 관리자 백업 코드 사용: 회원 #{} (남은 {}개)", memberId, remaining);
        alertMailer.sendAfterCommit(memberId, admin(memberId).getEmail(), "[CartMate] 관리자 백업 코드가 사용되었습니다",
                "백업 코드로 로그인했습니다 (남은 " + remaining + "개). 본인이 아니라면 즉시 운영 책임자에게 알려 주세요.\n"
                        + "휴대폰을 잃어버렸다면 운영 책임자에게 OTP 재등록(Break-glass)을 요청해 주세요.");
        return LoginCheck.passedByBackupCode(remaining);
    }

    // remainingBackupCodes: 백업 코드로 통과했을 때만 남은 개수 (OTP로 통과·실패면 null)
    record LoginCheck(boolean passed, Integer remainingBackupCodes) {
        static LoginCheck passedByOtp() {
            return new LoginCheck(true, null);
        }

        static LoginCheck passedByBackupCode(int remaining) {
            return new LoginCheck(true, remaining);
        }

        static LoginCheck failed() {
            return new LoginCheck(false, null);
        }
    }

    private List<String> issueBackupCodes(Long memberId) {
        backupCodeRepository.deleteAllOf(memberId);
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = newBackupCode();
            codes.add(code);
            backupCodeRepository.save(MemberBackupCode.of(memberId, backupHash(memberId, code)));
        }
        return codes;
    }

    // 하이픈·공백·대소문자는 무시하고 비교한다
    private String backupHash(Long memberId, String code) {
        String normalized = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        return hasher.hash("backup:" + memberId, normalized);
    }

    private static String newBackupCode() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(BACKUP_ALPHABET.charAt(RANDOM.nextInt(BACKUP_ALPHABET.length())));
        }
        return sb.toString();
    }

    private Member admin(Long memberId) {
        Member member = memberSupport.findById(memberId)
                .orElseThrow(() -> new ServiceException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (member.getRole() != MemberRole.ADMIN) {
            throw new ServiceException(MemberErrorCode.MEMBER_OTP_ADMIN_ONLY);
        }
        return member;
    }
}
