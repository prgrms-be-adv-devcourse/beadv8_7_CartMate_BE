package com.cartmate.cartmate.boundedContext.etcService;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.etcService.delivery.in.DeliveryScheduler;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.boundedContext.etcService.trust.app.TrustFacade;
import com.cartmate.cartmate.boundedContext.etcService.trust.in.SellerTrustScheduler;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// etc-service 스케줄러가 ShedLock으로 한 곳에서만 도는지 확인한다.
// DB 없이 메모리 락 저장소로 스프링 컨텍스트를 띄워, 어노테이션이 실제로 실행을 막는지까지 본다.
class EtcSchedulerLockTest {

    private AnnotationConfigApplicationContext context;
    private InMemoryLockProvider lockProvider;
    private DeliveryRepository deliveryRepository;
    private TrustFacade trustFacade;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        lockProvider = context.getBean(InMemoryLockProvider.class);
        deliveryRepository = context.getBean(DeliveryRepository.class);
        trustFacade = context.getBean(TrustFacade.class);
        clearInvocations(deliveryRepository, trustFacade);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    static Stream<Arguments> jobs() {
        return Stream.of(
                Arguments.of("delivery.completeShipped", Duration.ofMinutes(5), Duration.ofSeconds(5)),
                Arguments.of("delivery.confirmDelivered", Duration.ofMinutes(30), Duration.ofMinutes(1)),
                Arguments.of("sellerTrust.recalculateAll", Duration.ofMinutes(30), Duration.ofMinutes(1))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("락이 비어 있으면 정한 이름과 시간으로 락을 잡고 작업을 실행한다")
    void runWithLock(String lockName, Duration lockAtMostFor, Duration lockAtLeastFor) {
        run(lockName);

        verifyQueried(lockName, 1);
        LockConfiguration taken = lockProvider.taken.get(lockName);
        assertThat(taken).isNotNull();
        assertThat(taken.getLockAtMostFor()).isEqualTo(lockAtMostFor);
        assertThat(taken.getLockAtLeastFor()).isEqualTo(lockAtLeastFor);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("다른 인스턴스가 락을 잡고 있으면 대상 조회조차 하지 않고 건너뛴다")
    void skipWhenOtherNodeHoldsLock(String lockName, Duration lockAtMostFor, Duration lockAtLeastFor) {
        lockProvider.holdByOtherNode(lockName);

        run(lockName);

        verifyQueried(lockName, 0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("빨리 끝나도 최소 유지 시간 동안은 락이 남아 바로 다시 돌지 않는다")
    void keepLockAtLeastFor(String lockName, Duration lockAtMostFor, Duration lockAtLeastFor) {
        run(lockName);
        run(lockName);

        verifyQueried(lockName, 1);
    }

    @Test
    @DisplayName("판매자 신뢰도 재계산은 서버 시간대와 상관없이 한국 시간 새벽 3시에 돈다")
    void sellerTrustRunsAtKoreanTime() throws NoSuchMethodException {
        Scheduled scheduled = SellerTrustScheduler.class.getMethod("recalculateAll").getAnnotation(Scheduled.class);
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");

        ZoneId seoul = ZoneId.of(scheduled.zone());
        ZonedDateTime utcMidnight = ZonedDateTime.of(LocalDateTime.of(2026, 11, 11, 0, 0), ZoneId.of("UTC"));
        ZonedDateTime next = CronExpression.parse("0 0 3 * * *").next(utcMidnight.withZoneSameInstant(seoul));

        assertThat(next.toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 12, 3, 0));
        assertThat(next.withZoneSameInstant(ZoneId.of("UTC")).getHour()).isEqualTo(18);
    }

    private void run(String lockName) {
        switch (lockName) {
            case "delivery.completeShipped" -> context.getBean(DeliveryScheduler.class).completeShipped();
            case "delivery.confirmDelivered" -> context.getBean(DeliveryScheduler.class).confirmDelivered();
            case "sellerTrust.recalculateAll" -> context.getBean(SellerTrustScheduler.class).recalculateAll();
            default -> throw new IllegalArgumentException(lockName);
        }
    }

    private void verifyQueried(String lockName, int count) {
        switch (lockName) {
            case "delivery.completeShipped" ->
                    verify(deliveryRepository, times(count)).findIdsByStatusAndShippedAtBefore(any(), any());
            case "delivery.confirmDelivered" ->
                    verify(deliveryRepository, times(count)).findIdsByStatusAndDeliveredAtBefore(any(), any());
            case "sellerTrust.recalculateAll" -> verify(trustFacade, times(count)).aggregateMetrics();
            default -> throw new IllegalArgumentException(lockName);
        }
        if (count == 0) {
            verify(trustFacade, never()).recalculate(any(), any());
        }
    }

    @Configuration
    @EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
    static class TestConfig {

        @Bean
        InMemoryLockProvider lockProvider() {
            return new InMemoryLockProvider();
        }

        @Bean
        DeliveryRepository deliveryRepository() {
            return mock(DeliveryRepository.class);
        }

        @Bean
        DeliveryFacade deliveryFacade() {
            return mock(DeliveryFacade.class);
        }

        @Bean
        TrustFacade trustFacade() {
            return mock(TrustFacade.class);
        }

        @Bean
        DeliveryScheduler deliveryScheduler(DeliveryRepository deliveryRepository, DeliveryFacade deliveryFacade) {
            return new DeliveryScheduler(deliveryRepository, deliveryFacade, 300, 7);
        }

        @Bean
        SellerTrustScheduler sellerTrustScheduler(TrustFacade trustFacade) {
            return new SellerTrustScheduler(trustFacade);
        }
    }

    // shedlock 표 대신 메모리에 "이름별로 언제까지 잠겨 있는지"를 적어 두는 락 저장소.
    static class InMemoryLockProvider implements LockProvider {

        private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();
        private final Map<String, LockConfiguration> taken = new ConcurrentHashMap<>();

        @Override
        public Optional<SimpleLock> lock(LockConfiguration configuration) {
            String name = configuration.getName();
            Instant now = Instant.now();
            Instant until = lockedUntil.get(name);
            if (until != null && until.isAfter(now)) {
                return Optional.empty();
            }
            lockedUntil.put(name, configuration.getLockAtMostUntil());
            taken.put(name, configuration);
            return Optional.of(() -> lockedUntil.put(name, configuration.getUnlockTime()));
        }

        void holdByOtherNode(String name) {
            lockedUntil.put(name, Instant.now().plus(Duration.ofHours(1)));
        }
    }
}
