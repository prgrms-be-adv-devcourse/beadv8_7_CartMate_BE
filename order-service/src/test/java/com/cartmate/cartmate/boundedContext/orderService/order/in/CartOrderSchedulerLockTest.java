package com.cartmate.cartmate.boundedContext.orderService.order.in;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.in.groupshared.SharedGroupPurchaseScheduler;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.in.groupshared.SharedGroupOrderScheduler;
import com.cartmate.cartmate.boundedContext.orderService.order.in.personal.OrderScheduler;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.ballLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedLine;
import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class CartOrderSchedulerLockTest {

    private static final String OTHER_NODE = "other-node";
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(5);
    private static final Long INVENTORY_ID = 4L;

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private SharedGroupOrderScheduler sharedGroupOrderScheduler;

    @Autowired
    private SharedGroupPurchaseScheduler sharedGroupPurchaseScheduler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long base = 9_700_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @BeforeEach
    void setUp() {
        jobs().map(arguments -> (String) arguments.get()[0]).forEach(name -> jdbcTemplate.update(
                "INSERT INTO shedlock (name, lock_until, locked_at, locked_by) "
                        + "VALUES (?, UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE, UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE, 'test-reset') "
                        + "ON DUPLICATE KEY UPDATE lock_until = VALUES(lock_until), locked_at = VALUES(locked_at), "
                        + "locked_by = VALUES(locked_by)", name));
    }

    static Stream<Arguments> jobs() {
        return Stream.of(
                Arguments.of("order.cancelExpired", OrderStatus.PAYMENT_PENDING.name(), OrderStatus.CANCELED.name()),
                Arguments.of("sharedGroupOrder.cancelExpired", SharedGroupOrderStatus.PAYING.name(), SharedGroupOrderStatus.CANCELED.name()),
                Arguments.of("sharedGroupPurchase.closeExpired", SharedGroupPurchaseStatus.OPEN.name(), SharedGroupPurchaseStatus.CLOSED.name()),
                Arguments.of("sharedGroupPurchase.closeFrozenTimeout", SharedGroupPurchaseStatus.FROZEN.name(), SharedGroupPurchaseStatus.CLOSED.name()),
                Arguments.of("sharedGroupPurchase.revertOrderTimeout", SharedGroupPurchaseStatus.CLOSED.name(), SharedGroupPurchaseStatus.FROZEN.name())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("락이 비어 있으면 처리 대상을 처리하고, 이름별 락 행이 남아 빨리 끝나도 최소 유지 시간 동안 잡혀 있다")
    void runProcessesTargetAndLeavesLockRow(String lockName, String before, String after) {
        Long targetId = seedTarget(lockName);
        assertThat(statusOf(lockName, targetId)).isEqualTo(before);

        run(lockName);

        assertThat(statusOf(lockName, targetId)).isEqualTo(after);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT locked_by, TIMESTAMPDIFF(MICROSECOND, locked_at, lock_until) AS held_micros "
                        + "FROM shedlock WHERE name = ?", lockName);
        assertThat(row.get("locked_by")).isNotIn(OTHER_NODE, "test-reset");
        assertThat(((Number) row.get("held_micros")).longValue()).isGreaterThanOrEqualTo(LOCK_AT_LEAST.toNanos() / 1000);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jobs")
    @DisplayName("다른 인스턴스가 락을 잡고 있으면 처리 대상이 있어도 건드리지 않고 건너뛴다")
    void skipWhenOtherNodeHoldsLock(String lockName, String before, String after) {
        Long targetId = seedTarget(lockName);
        jdbcTemplate.update("UPDATE shedlock SET lock_until = UTC_TIMESTAMP(3) + INTERVAL 1 HOUR, "
                + "locked_at = UTC_TIMESTAMP(3), locked_by = ? WHERE name = ?", OTHER_NODE, lockName);

        run(lockName);

        assertThat(statusOf(lockName, targetId)).isEqualTo(before);
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

    private Long seedTarget(String lockName) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        return switch (lockName) {
            case "order.cancelExpired" -> expiredOrder(now);
            case "sharedGroupOrder.cancelExpired" -> expiredSharedGroupOrder(now);
            case "sharedGroupPurchase.closeExpired" -> sharedGroupPurchase(now, purchase -> {
            });
            case "sharedGroupPurchase.closeFrozenTimeout" -> sharedGroupPurchase(now, purchase ->
                    purchase.closeByDeadline(Set.of(INVENTORY_ID),
                            now.minus(SharedGroupPurchase.FROZEN_PERIOD).minusHours(1)));
            case "sharedGroupPurchase.revertOrderTimeout" -> sharedGroupPurchase(now, purchase ->
                    purchase.closeByDeadline(Set.of(), now.minusHours(1)));
            default -> throw new IllegalArgumentException(lockName);
        };
    }

    private Long expiredOrder(LocalDateTime now) {
        LocalDateTime createdAt = now.minus(Order.PAYMENT_PERIOD).minusMinutes(5);
        return orderRepository.save(personalOrder("LOCK-" + nextId(), nextId(), List.of(ballLine(1L, 1)), createdAt)).getId();
    }

    private Long expiredSharedGroupOrder(LocalDateTime now) {
        LocalDateTime closedAt = now.minus(SharedGroupOrder.PAYMENT_PERIOD).minus(SharedGroupOrder.GRACE_PERIOD).minusMinutes(5);
        long creator = nextId();
        return transactionTemplate.execute(status -> {
            SharedGroupOrder order = SharedGroupOrder.create(nextId(), creator, List.of(creator, nextId()),
                    List.of(sharedLine(2L, INVENTORY_ID, 1L, 1, 30000L)), closedAt);
            order.markStockReserved();
            return sharedGroupOrderRepository.save(order).getId();
        });
    }

    private Long sharedGroupPurchase(LocalDateTime now, Consumer<SharedGroupPurchase> prepare) {
        LocalDateTime createdAt = now.minus(SharedGroupPurchase.EDIT_PERIOD)
                .minus(SharedGroupPurchase.FROZEN_PERIOD).minusHours(2);
        long creator = nextId();
        return transactionTemplate.execute(status -> {
            SharedGroupPurchase purchase = SharedGroupPurchase.create("락 확인", creator, 5, "lk" + nextId() % 10_000_000_000L, createdAt);
            purchase.addItem(creator, 2L, INVENTORY_ID, 1, createdAt);
            prepare.accept(purchase);
            return sharedGroupPurchaseRepository.save(purchase).getId();
        });
    }

    private String statusOf(String lockName, Long id) {
        return switch (lockName) {
            case "order.cancelExpired" -> orderRepository.findById(id).orElseThrow().getStatus().name();
            case "sharedGroupOrder.cancelExpired" -> sharedGroupOrderRepository.findById(id).orElseThrow().getStatus().name();
            default -> sharedGroupPurchaseRepository.findById(id).orElseThrow().getStatus().name();
        };
    }

    private long nextId() {
        return base + sequence++;
    }
}
