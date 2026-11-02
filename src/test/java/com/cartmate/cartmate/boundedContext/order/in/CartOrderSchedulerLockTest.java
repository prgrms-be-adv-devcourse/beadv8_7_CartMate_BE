package com.cartmate.cartmate.boundedContext.order.in;

import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.in.groupshared.SharedGroupPurchaseScheduler;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.order.in.groupshared.SharedGroupOrderScheduler;
import com.cartmate.cartmate.boundedContext.order.in.personal.OrderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** cart · order 스케줄러 4개가 ShedLock으로 한 인스턴스에서만 돈다. 다른 인스턴스가 락을 잡고 있으면 건너뛴다. */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class CartOrderSchedulerLockTest {

    private static final String OTHER_NODE = "other-node";

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

    // 락 행을 지우지 않고 만료시킨다. ShedLock은 한 번 만든 락 이름을 기억하고 그다음부터 UPDATE만 하므로,
    // 행을 지우면 UPDATE가 0건이 되어 계속 "잠김"으로 보고 건너뛴다 (운영에서도 락을 풀 때는 lock_until만 과거로 돌린다)
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
                Arguments.of("sharedGroupPurchase.closeFrozenTimeout")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("한 번 돌면 이름별 락 행이 남고, 빨리 끝나도 최소 유지 시간 동안 잡혀 있다")
    void runLeavesLockRow(String lockName) {
        run(lockName);

        verifyQueried(lockName, 1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT locked_by, lock_until > UTC_TIMESTAMP(3) AS held FROM shedlock WHERE name = ?", lockName);
        assertThat(row.get("locked_by")).isNotIn(OTHER_NODE, "test-reset");
        assertThat(((Number) row.get("held")).intValue()).isEqualTo(1);
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
            default -> throw new IllegalArgumentException(lockName);
        }
    }

    private void verifyQueried(String lockName, int times) {
        switch (lockName) {
            case "order.cancelExpired" -> verify(orderFacade, times(times)).findIdsToCancelByDeadline(any());
            case "sharedGroupOrder.cancelExpired" -> verify(sharedGroupOrderFacade, times(times)).findIdsToCancelByDeadline(any());
            case "sharedGroupPurchase.closeExpired" -> verify(sharedGroupPurchaseFacade, times(times)).findIdsToCloseByDeadline(any());
            case "sharedGroupPurchase.closeFrozenTimeout" -> verify(sharedGroupPurchaseFacade, times(times)).findIdsToReleaseFrozen(any());
            default -> throw new IllegalArgumentException(lockName);
        }
    }
}
