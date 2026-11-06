package com.cartmate.cartmate.boundedContext.orderService.order.in;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.in.groupshared.SharedGroupPurchaseScheduler;
import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.in.groupshared.SharedGroupOrderScheduler;
import com.cartmate.cartmate.boundedContext.orderService.order.in.personal.OrderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.cartmate.cartmate.support.CartMateTestContainers;
import com.cartmate.cartmate.support.DatabaseCleaner;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
@ImportTestcontainers(CartMateTestContainers.class)
@ExtendWith(DatabaseCleaner.class)
class CartOrderSchedulerLockTest {

    private static final String OTHER_NODE = "other-node";
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(5);

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private SharedGroupOrderScheduler sharedGroupOrderScheduler;

    @Autowired
    private SharedGroupPurchaseScheduler sharedGroupPurchaseScheduler;

    @MockitoSpyBean
    private OrderFacade orderFacade;

    @MockitoSpyBean
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @MockitoSpyBean
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobs().map(arguments -> (String) arguments.get()[0]).forEach(name -> jdbcTemplate.update(
                "INSERT INTO shedlock (name, lock_until, locked_at, locked_by) "
                        + "VALUES (?, UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE, UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE, 'test-reset') "
                        + "ON DUPLICATE KEY UPDATE lock_until = VALUES(lock_until), locked_at = VALUES(locked_at), "
                        + "locked_by = VALUES(locked_by)", name));
        clearInvocations(orderFacade, sharedGroupOrderFacade, sharedGroupPurchaseFacade);
    }

    static Stream<Arguments> jobs() {
        return Stream.of(
                Arguments.of("order.cancelExpired"),
                Arguments.of("sharedGroupOrder.cancelExpired"),
                Arguments.of("sharedGroupPurchase.closeExpired"),
                Arguments.of("sharedGroupPurchase.closeFrozenTimeout"),
                Arguments.of("sharedGroupPurchase.revertOrderTimeout")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("한 번 돌면 이름별 락 행이 남고, 빨리 끝나도 최소 유지 시간 동안 잡혀 있다")
    void runLeavesLockRow(String lockName) {
        run(lockName);

        verifyQueried(lockName, 1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT locked_by, TIMESTAMPDIFF(MICROSECOND, locked_at, lock_until) AS held_micros "
                        + "FROM shedlock WHERE name = ?", lockName);
        assertThat(row.get("locked_by")).isNotIn(OTHER_NODE, "test-reset");
        assertThat(((Number) row.get("held_micros")).longValue()).isGreaterThanOrEqualTo(LOCK_AT_LEAST.toNanos() / 1000);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("다른 인스턴스가 락을 잡고 있으면 대상 조회조차 하지 않고 건너뛴다")
    void skipWhenOtherNodeHoldsLock(String lockName) {
        jdbcTemplate.update("UPDATE shedlock SET lock_until = UTC_TIMESTAMP(3) + INTERVAL 1 HOUR, "
                + "locked_at = UTC_TIMESTAMP(3), locked_by = ? WHERE name = ?", OTHER_NODE, lockName);

        run(lockName);

        verifyQueried(lockName, 0);
        assertThat(jdbcTemplate.queryForObject("SELECT locked_by FROM shedlock WHERE name = ?", String.class, lockName))
                .isEqualTo(OTHER_NODE);
    }

    private void run(String lockName) {
        switch (lockName) {
            case "order.cancelExpired" -> orderScheduler.cancelExpired();
            case "sharedGroupOrder.cancelExpired" -> sharedGroupOrderScheduler.cancelExpired();
            case "sharedGroupPurchase.closeExpired" -> sharedGroupPurchaseScheduler.closeExpired();
            case "sharedGroupPurchase.closeFrozenTimeout" -> sharedGroupPurchaseScheduler.closeFrozenTimeout();
            case "sharedGroupPurchase.revertOrderTimeout" -> sharedGroupPurchaseScheduler.revertOrderTimeout();
            default -> throw new IllegalArgumentException(lockName);
        }
    }

    private void verifyQueried(String lockName, int times) {
        switch (lockName) {
            case "order.cancelExpired" -> verify(orderFacade, times(times)).findIdsToCancelByDeadline(any());
            case "sharedGroupOrder.cancelExpired" -> verify(sharedGroupOrderFacade, times(times)).findIdsToCancelByDeadline(any());
            case "sharedGroupPurchase.closeExpired" -> verify(sharedGroupPurchaseFacade, times(times)).findIdsToCloseByDeadline(any());
            case "sharedGroupPurchase.closeFrozenTimeout" -> verify(sharedGroupPurchaseFacade, times(times)).findIdsToReleaseFrozen(any());
            case "sharedGroupPurchase.revertOrderTimeout" -> verify(sharedGroupPurchaseFacade, times(times)).findIdsAwaitingOrderTooLong(any());
            default -> throw new IllegalArgumentException(lockName);
        }
    }
}
