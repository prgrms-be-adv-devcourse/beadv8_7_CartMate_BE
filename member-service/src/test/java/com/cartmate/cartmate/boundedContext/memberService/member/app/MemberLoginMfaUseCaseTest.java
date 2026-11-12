package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.EmailVerificationProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MfaProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.LoginOutcome;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.TokenPair;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationMailSender;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 로그인 2차 인증의 보안 규칙. challenge · 인증번호 · 계정 실패 횟수가 Redis Lua 스크립트에 있으므로 실제 Redis로 확인한다
class MemberLoginMfaUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final TokenPair TOKENS = new TokenPair("access", "refresh", false, Duration.ofDays(1));

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;

    private final MemberSupport memberSupport = mock(MemberSupport.class);
    private final MemberTokenUseCase tokenUseCase = mock(MemberTokenUseCase.class);
    private final TrustedDeviceUseCase trustedDevices = mock(TrustedDeviceUseCase.class);
    private final AdminOtpUseCase adminOtp = mock(AdminOtpUseCase.class); // 기본: OTP 미등록 → 이메일 경로
    private final CapturingMailSender mail = new CapturingMailSender();
    private MemberLoginMfaUseCase useCase;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        redis.stop();
    }

    @BeforeEach
    void setUp() {
        connectionFactory.getConnection().serverCommands().flushAll();
        useCase = new MemberLoginMfaUseCase(memberSupport, tokenUseCase, trustedDevices,
                new VerificationCodeStore(connectionFactory),
                new VerificationCodeHasher("test-secret-test-secret-test-secret-32"), mail,
                new EmailVerificationProperties(null, null, null, null, 0, 0),
                new MfaProperties(true, null, null, 0, null),
                adminOtp);
        when(tokenUseCase.issue(any(), anyBoolean())).thenReturn(TOKENS);
        when(tokenUseCase.issue(any(), anyBoolean(), anyList())).thenReturn(TOKENS);
    }

    private Member member(long id, MemberRole role) {
        Member member = Member.create("buyer@cartmate.com", "encoded", "닉네임", "010-1234-5678", LocalDate.of(1995, 5, 24));
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        when(memberSupport.findById(id)).thenReturn(Optional.of(member));
        return member;
    }

    private String login(Member member, boolean remember) {
        return useCase.afterPassword(member, remember, null, NOW).challenge().challengeId();
    }

    @Test
    void 비밀번호만_맞으면_토큰_없이_인증번호를_보낸다() {
        Member member = member(1L, MemberRole.BUYER);
        when(trustedDevices.useForLogin(eq(1L), any(), any())).thenReturn(Optional.empty());

        LoginOutcome outcome = useCase.afterPassword(member, false, null, NOW);

        assertThat(outcome.mfaRequired()).isTrue();
        assertThat(outcome.challenge().maskedEmail()).isEqualTo("bu***@cartmate.com");
        assertThat(mail.to).isEqualTo("buyer@cartmate.com");
        verify(tokenUseCase, never()).issue(any(), anyBoolean());
        verify(tokenUseCase, never()).issue(any(), anyBoolean(), anyList());
    }

    @Test
    void 신뢰_기기면_인증_없이_토큰과_새_기기_토큰() {
        Member member = member(1L, MemberRole.BUYER);
        when(trustedDevices.useForLogin(eq(1L), eq("old-device"), any())).thenReturn(Optional.of("new-device"));

        LoginOutcome outcome = useCase.afterPassword(member, true, "old-device", NOW);

        assertThat(outcome.result().tokens()).isEqualTo(TOKENS);
        assertThat(outcome.trustedDeviceToken()).isEqualTo("new-device");
        assertThat(mail.to).isNull();
    }

    @Test
    void 관리자는_신뢰_기기가_있어도_매번_인증() {
        Member admin = member(9L, MemberRole.ADMIN);

        LoginOutcome outcome = useCase.afterPassword(admin, false, "device", NOW);

        assertThat(outcome.mfaRequired()).isTrue();
        verify(trustedDevices, never()).useForLogin(any(), any(), any());
    }

    @Test
    void 코드가_맞으면_토큰과_기기_기억_이메일_확인을_기록하고_같은_challenge는_다시_못_쓴다() {
        Member member = member(1L, MemberRole.BUYER);
        String challengeId = login(member, true);
        when(trustedDevices.register(eq(1L), eq("Chrome"), any())).thenReturn("device-token");

        LoginOutcome outcome = useCase.verify(challengeId, mail.lastCode(), true, "Chrome", NOW.plusSeconds(10));

        assertThat(outcome.result().tokens()).isEqualTo(TOKENS);
        assertThat(outcome.trustedDeviceToken()).isEqualTo("device-token");
        assertThat(member.getEmailVerifiedAt()).isNotNull();
        verify(tokenUseCase).issue(member, true, List.of("pwd", "email")); // 로그인 유지 여부가 challenge에 저장돼 넘어온다
        assertThatThrownBy(() -> useCase.verify(challengeId, mail.lastCode(), false, "Chrome", NOW.plusSeconds(11)))
                .hasMessageContaining("다시 로그인");
    }

    @Test
    void 맞는_코드를_동시에_여러_번_보내도_토큰은_한_번만() throws Exception {
        Member member = member(1L, MemberRole.BUYER);
        String challengeId = login(member, false);
        String code = mail.lastCode();
        Callable<Boolean> attempt = () -> {
            try {
                useCase.verify(challengeId, code, false, "Chrome", NOW.plusSeconds(10));
                return true;
            } catch (ServiceException e) {
                return false;
            }
        };

        List<Boolean> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            results = pool.invokeAll(IntStream.range(0, 10).mapToObj(i -> attempt).toList()).stream()
                    .map(MemberLoginMfaUseCaseTest::get).toList();
        }

        assertThat(results).containsOnlyOnce(true);
        verify(tokenUseCase, times(1)).issue(any(), anyBoolean(), anyList());
    }

    @Test
    void 다섯_번_틀리면_challenge가_끝나_다시_로그인해야_한다() {
        Member member = member(1L, MemberRole.BUYER);
        String challengeId = login(member, false);
        String wrong = wrongCode();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> useCase.verify(challengeId, wrong, false, null, NOW.plusSeconds(10)))
                    .hasMessageContaining("일치하지 않습니다");
        }

        assertThatThrownBy(() -> useCase.verify(challengeId, mail.lastCode(), false, null, NOW.plusSeconds(11)))
                .hasMessageContaining("시도 횟수를 초과");
        assertThatThrownBy(() -> useCase.verify(challengeId, mail.lastCode(), false, null, NOW.plusSeconds(12)))
                .hasMessageContaining("다시 로그인");
    }

    @Test
    void 새로_로그인해도_계정당_10번_틀리면_잠긴다() {
        Member member = member(1L, MemberRole.BUYER);
        for (int round = 0; round < 2; round++) {
            String challengeId = login(member, false);
            String wrong = wrongCode();
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> useCase.verify(challengeId, wrong, false, null, NOW.plusSeconds(10)))
                        .isInstanceOf(ServiceException.class);
            }
        }

        assertThatThrownBy(() -> useCase.afterPassword(member, false, null, NOW.plusSeconds(20)))
                .hasMessageContaining("잠시 로그인할 수 없습니다");
    }

    @Test
    void 재발송은_60초_뒤부터_되고_없는_challenge는_다시_로그인() {
        Member member = member(1L, MemberRole.BUYER);
        String challengeId = login(member, false);

        assertThatThrownBy(() -> useCase.resend(challengeId, NOW.plusSeconds(30)))
                .hasMessageContaining("초 후에 다시 요청");
        assertThat(useCase.resend(challengeId, NOW.plusSeconds(61))).isEqualTo(300);
        useCase.verify(challengeId, mail.lastCode(), false, null, NOW.plusSeconds(62));

        assertThatThrownBy(() -> useCase.resend("unknown", NOW)).hasMessageContaining("다시 로그인");
    }

    // ===== 관리자 OTP =====

    @Test
    void OTP를_등록한_관리자는_메일_없이_OTP를_요구하고_재발송은_없다() {
        Member admin = member(9L, MemberRole.ADMIN);
        when(adminOtp.isEnabled(9L)).thenReturn(true);

        LoginOutcome outcome = useCase.afterPassword(admin, false, null, NOW);

        assertThat(outcome.challenge().method()).isEqualTo("OTP");
        assertThat(outcome.challenge().maskedEmail()).isNull();
        assertThat(mail.to).isNull();
        assertThatThrownBy(() -> useCase.resend(outcome.challenge().challengeId(), NOW.plusSeconds(61)))
                .hasMessageContaining("OTP 앱에 표시된 코드");
    }

    @Test
    void OTP가_맞으면_otp를_거친_토큰이고_같은_challenge는_다시_못_쓴다() {
        Member admin = member(9L, MemberRole.ADMIN);
        when(adminOtp.isEnabled(9L)).thenReturn(true);
        when(adminOtp.verifyForLogin(eq(9L), eq("123456"), any())).thenReturn(AdminOtpUseCase.LoginCheck.passedByOtp());
        String challengeId = useCase.afterPassword(admin, false, null, NOW).challenge().challengeId();

        LoginOutcome outcome = useCase.verify(challengeId, "123456", true, "Chrome", NOW.plusSeconds(5));

        assertThat(outcome.result().tokens()).isEqualTo(TOKENS);
        assertThat(outcome.trustedDeviceToken()).isNull(); // 관리자는 기기 기억 없음
        verify(tokenUseCase).issue(admin, false, List.of("pwd", "otp"));
        assertThatThrownBy(() -> useCase.verify(challengeId, "123456", false, null, NOW.plusSeconds(6)))
                .hasMessageContaining("다시 로그인");
    }

    @Test
    void OTP를_다섯_번_틀리면_challenge가_끝난다() {
        Member admin = member(9L, MemberRole.ADMIN);
        when(adminOtp.isEnabled(9L)).thenReturn(true);
        when(adminOtp.verifyForLogin(eq(9L), any(), any())).thenReturn(AdminOtpUseCase.LoginCheck.failed());
        String challengeId = useCase.afterPassword(admin, false, null, NOW).challenge().challengeId();

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> useCase.verify(challengeId, "000000", false, null, NOW.plusSeconds(5)))
                    .hasMessageContaining("OTP 코드가 일치하지 않습니다");
        }
        assertThatThrownBy(() -> useCase.verify(challengeId, "000000", false, null, NOW.plusSeconds(5)))
                .hasMessageContaining("시도 횟수를 초과");
        assertThatThrownBy(() -> useCase.verify(challengeId, "000000", false, null, NOW.plusSeconds(6)))
                .hasMessageContaining("다시 로그인");
        verify(tokenUseCase, never()).issue(any(), anyBoolean(), anyList());
    }

    @Test
    void OTP를_등록하지_않은_관리자는_메일_코드_뒤_OTP_등록_전용_세션() {
        Member admin = member(9L, MemberRole.ADMIN);
        String challengeId = login(admin, true);

        LoginOutcome outcome = useCase.verify(challengeId, mail.lastCode(), true, "Chrome", NOW.plusSeconds(10));

        assertThat(outcome.otpEnrollmentRequired()).isTrue();
        assertThat(outcome.trustedDeviceToken()).isNull();
        verify(tokenUseCase).issue(admin, true, List.of("pwd", "email")); // otp 없음 → 관리자 API 403
        verify(trustedDevices, never()).register(any(), any(), any());
    }

    private String wrongCode() {
        return mail.lastCode().equals("000000") ? "111111" : "000000";
    }

    private static boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static class CapturingMailSender implements VerificationMailSender {
        private volatile String to;
        private volatile String body;

        @Override
        public void send(String to, String subject, String body) {
            this.to = to;
            this.body = body;
        }

        String lastCode() {
            Matcher m = Pattern.compile("\\d{6}").matcher(body);
            assertThat(m.find()).isTrue();
            return m.group();
        }
    }
}
