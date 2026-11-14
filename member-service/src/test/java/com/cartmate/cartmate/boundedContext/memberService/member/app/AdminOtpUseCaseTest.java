package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberOtp;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberBackupCodeRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberOtpRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationMailSender;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 관리자 OTP의 우회 방지 규칙: 관리자만, 이미 등록된 OTP는 다시 등록 불가, 같은 구간 코드 재사용 불가, 백업 코드는 한 번만,
// 알림 메일은 커밋된 뒤에만
class AdminOtpUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private final MemberSupport memberSupport = mock(MemberSupport.class);
    private final MemberOtpRepository otpRepository = mock(MemberOtpRepository.class);
    private final MemberBackupCodeRepository backupCodes = mock(MemberBackupCodeRepository.class);
    private final TotpVerifier totp = mock(TotpVerifier.class);
    private final VerificationMailSender mail = mock(VerificationMailSender.class);
    private final OtpSecretCipher cipher = new OtpSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));

    // 메일은 바로 실행해 확인한다 (운영은 다른 스레드)
    private final AdminOtpUseCase useCase = new AdminOtpUseCase(memberSupport, otpRepository, backupCodes, cipher, totp,
            new VerificationCodeHasher("test-secret-test-secret-test-secret-32"),
            new SecurityAlertMailer(mail, Runnable::run));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Member member(long id, MemberRole role) {
        Member member = Member.create("admin@cartmate.com", "encoded", "관리자", "010-1234-5678", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        when(memberSupport.findById(id)).thenReturn(Optional.of(member));
        return member;
    }

    private void enabledOtp(long memberId) {
        MemberOtp otp = MemberOtp.pending(memberId, cipher.encrypt(new byte[20]));
        otp.enable(100L, LocalDateTime.now());
        when(otpRepository.findByMemberId(memberId)).thenReturn(Optional.of(otp));
    }

    @Test
    void 관리자가_아니면_OTP를_등록할_수_없다() {
        member(1L, MemberRole.BUYER);
        assertThatThrownBy(() -> useCase.startEnrollment(1L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("관리자 계정만");
    }

    @Test
    void 이미_등록된_OTP는_다시_등록할_수_없다_바꾸는_길은_Break_glass뿐() {
        member(1L, MemberRole.ADMIN);
        enabledOtp(1L);
        assertThatThrownBy(() -> useCase.startEnrollment(1L)).hasMessageContaining("이미 OTP");
    }

    @Test
    void 등록_시작을_두_번_불러도_같은_비밀키() {
        member(1L, MemberRole.ADMIN);
        when(totp.newSecret()).thenReturn(new byte[]{1, 2, 3, 4, 5}, new byte[]{9, 9, 9, 9, 9}); // 새로 만들면 달라진다
        when(totp.base32(any())).thenAnswer(inv -> new TotpVerifier().base32(inv.getArgument(0)));
        when(otpRepository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(otpRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MemberOtp saved = inv.getArgument(0);
            when(otpRepository.findByMemberId(1L)).thenReturn(Optional.of(saved)); // 다음 조회부터 저장된 행
            return saved;
        });

        String first = useCase.startEnrollment(1L).secret();
        String second = useCase.startEnrollment(1L).secret();

        assertThat(second).isEqualTo(first).isEqualTo(new TotpVerifier().base32(new byte[]{1, 2, 3, 4, 5}));
        verify(otpRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void 비밀키는_암호화해_저장하고_풀면_원래_값() {
        byte[] secret = "12345678901234567890".getBytes();
        String stored = cipher.encrypt(secret);
        assertThat(stored).startsWith("v1:").doesNotContain(Base64.getEncoder().encodeToString(secret));
        assertThat(cipher.decrypt(stored)).isEqualTo(secret);
        assertThat(cipher.encrypt(secret)).isNotEqualTo(stored); // IV가 매번 달라 같은 키도 다르게 저장
    }

    @Test
    void 첫_코드가_맞아야_활성화되고_백업_코드_10개를_준다() {
        member(1L, MemberRole.ADMIN);
        MemberOtp pending = MemberOtp.pending(1L, cipher.encrypt(new byte[20]));
        when(otpRepository.findByMemberId(1L)).thenReturn(Optional.of(pending));
        when(totp.verify(any(), eq("123456"), any())).thenReturn(OptionalLong.of(55L));

        List<String> codes = useCase.confirmEnrollment(1L, "123456", NOW);

        assertThat(pending.isEnabled()).isTrue();
        assertThat(pending.getLastUsedStep()).isEqualTo(55L); // 등록에 쓴 코드는 로그인에 다시 못 쓴다
        assertThat(codes).hasSize(10).doesNotHaveDuplicates()
                .allMatch(c -> c.matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"));
        verify(backupCodes, times(10)).save(any());
        verify(mail).send(eq("admin@cartmate.com"), anyString(), anyString()); // 등록 알림
    }

    @Test
    void 같은_30초_구간의_코드를_다시_쓰면_실패() {
        member(1L, MemberRole.ADMIN);
        enabledOtp(1L);
        when(totp.verify(any(), eq("123456"), any())).thenReturn(OptionalLong.of(120L));
        when(otpRepository.markUsed(1L, 120L)).thenReturn(1).thenReturn(0); // 두 번째는 이미 쓴 구간

        assertThat(useCase.verifyForLogin(1L, "123456", NOW).passed()).isTrue();
        assertThat(useCase.verifyForLogin(1L, "123456", NOW).passed()).isFalse();
    }

    @Test
    void 백업_코드는_한_번만_쓰이고_대소문자_하이픈은_무시한다() {
        member(1L, MemberRole.ADMIN);
        enabledOtp(1L);
        when(backupCodes.use(eq(1L), anyString())).thenReturn(1).thenReturn(0);
        when(backupCodes.countByMemberId(1L)).thenReturn(9L);

        AdminOtpUseCase.LoginCheck first = useCase.verifyForLogin(1L, "ABCD-EFGH-2345", NOW);
        AdminOtpUseCase.LoginCheck second = useCase.verifyForLogin(1L, "abcdefgh2345", NOW);

        assertThat(first.passed()).isTrue();
        assertThat(first.remainingBackupCodes()).isEqualTo(9);
        assertThat(second.passed()).isFalse();
        // 두 번 모두 같은 해시로 찾는다 (정규화)
        verify(backupCodes, times(2)).use(eq(1L), eq(new VerificationCodeHasher("test-secret-test-secret-test-secret-32")
                .hash("backup:1", "ABCDEFGH2345")));
        verify(mail).send(eq("admin@cartmate.com"), anyString(), anyString()); // 사용 알림 (성공한 한 번만)
    }

    @Test
    void 트랜잭션_안이면_알림_메일은_커밋된_뒤에만_보내고_롤백되면_안_보낸다() {
        member(1L, MemberRole.ADMIN);
        enabledOtp(1L);
        when(backupCodes.use(eq(1L), anyString())).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        useCase.verifyForLogin(1L, "ABCD-EFGH-2345", NOW);
        verify(mail, never()).send(anyString(), anyString(), anyString()); // 아직 커밋 전

        List<TransactionSynchronization> rolledBack = TransactionSynchronizationManager.getSynchronizations();
        rolledBack.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(mail, never()).send(anyString(), anyString(), anyString()); // 롤백 → 안 보냄

        rolledBack.forEach(TransactionSynchronization::afterCommit);
        verify(mail).send(eq("admin@cartmate.com"), anyString(), anyString()); // 커밋 → 보냄
    }
}
