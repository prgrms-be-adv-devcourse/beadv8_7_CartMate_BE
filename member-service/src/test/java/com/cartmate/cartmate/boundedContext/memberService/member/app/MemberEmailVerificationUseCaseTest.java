package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.EmailVerificationProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationMailSender;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.mail.MailSendException;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// 가입 이메일 확인의 보안 규칙. 규칙이 Redis Lua 스크립트에 있으므로 실제 Redis(Testcontainers)로 확인한다
class MemberEmailVerificationUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private static final String EMAIL = "new@cartmate.com";

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;

    private final CapturingMailSender mail = new CapturingMailSender();
    private MemberEmailVerificationUseCase useCase;

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
        useCase = new MemberEmailVerificationUseCase(
                new VerificationCodeStore(connectionFactory),
                new VerificationCodeHasher("test-secret-test-secret-test-secret-32"),
                mail, mock(MemberSupport.class),
                new EmailVerificationProperties(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofSeconds(60),
                        Duration.ofMinutes(30), 5, 5));
    }

    @Test
    void 맞는_코드면_확인되고_다시_보낸_뒤엔_이전_코드를_쓸_수_없다() {
        useCase.send(EMAIL, NOW);
        String first = mail.lastCode();
        useCase.send(EMAIL, NOW.plusSeconds(61));
        String second = mail.lastCode();

        if (!first.equals(second)) {
            assertThatThrownBy(() -> useCase.verify(EMAIL, first, NOW.plusSeconds(62)))
                    .hasMessageContaining("일치하지 않습니다");
        }
        useCase.verify(EMAIL, second, NOW.plusSeconds(63));

        assertThat(useCase.isVerified(" NEW@cartmate.com ", NOW.plusSeconds(64))).isTrue(); // 대소문자·공백 무시
        useCase.consume(EMAIL);
        assertThat(useCase.isVerified(EMAIL, NOW.plusSeconds(65))).isFalse();
    }

    @Test
    void 재발송은_60초가_지나야_한다() {
        useCase.send(EMAIL, NOW);
        assertThatThrownBy(() -> useCase.send(EMAIL, NOW.plusSeconds(30)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("31초 후에 다시 요청");
    }

    @Test
    void 다섯_번_틀리면_막히고_다시_보내도_틀린_횟수가_초기화되지_않는다() {
        useCase.send(EMAIL, NOW);
        String wrong = wrongCode();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> useCase.verify(EMAIL, wrong, NOW.plusSeconds(10)))
                    .hasMessageContaining("일치하지 않습니다");
        }

        assertThatThrownBy(() -> useCase.verify(EMAIL, mail.lastCode(), NOW.plusSeconds(11)))
                .hasMessageContaining("시도 횟수를 초과");
        assertThatThrownBy(() -> useCase.send(EMAIL, NOW.plusSeconds(120)))
                .hasMessageContaining("시도 횟수를 초과");
    }

    @Test
    void 틀린_코드를_동시에_많이_보내도_다섯_번까지만_맞춰_볼_수_있다() throws Exception {
        useCase.send(EMAIL, NOW);
        String wrong = wrongCode();
        Callable<String> guess = () -> {
            try {
                useCase.verify(EMAIL, wrong, NOW.plusSeconds(10));
                return "ok";
            } catch (ServiceException e) {
                return e.getMessage();
            }
        };

        List<String> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            List<Future<String>> futures = pool.invokeAll(IntStream.range(0, 20).mapToObj(i -> guess).toList());
            results = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        }

        assertThat(results).filteredOn(m -> m.contains("일치하지 않습니다")).hasSize(5);
        assertThat(results).filteredOn(m -> m.contains("시도 횟수를 초과")).hasSize(15);
    }

    @Test
    void 만료된_코드는_맞아도_거절() {
        useCase.send(EMAIL, NOW);
        assertThatThrownBy(() -> useCase.verify(EMAIL, mail.lastCode(), NOW.plusSeconds(301)))
                .hasMessageContaining("만료");
    }

    @Test
    void 메일을_못_보내면_바로_다시_요청할_수_있다() {
        mail.failNext = true;
        assertThatThrownBy(() -> useCase.send(EMAIL, NOW)).hasMessageContaining("보내지 못했습니다");

        useCase.send(EMAIL, NOW.plusSeconds(1));
        useCase.verify(EMAIL, mail.lastCode(), NOW.plusSeconds(2));
    }

    private String wrongCode() {
        return mail.lastCode().equals("000000") ? "111111" : "000000";
    }

    static class CapturingMailSender implements VerificationMailSender {
        private volatile String body;
        private volatile boolean failNext;

        @Override
        public void send(String to, String subject, String body) {
            if (failNext) {
                failNext = false;
                throw new MailSendException("SMTP 연결 실패");
            }
            this.body = body;
        }

        String lastCode() {
            Matcher m = Pattern.compile("\\d{6}").matcher(body);
            assertThat(m.find()).isTrue();
            return m.group();
        }
    }
}
