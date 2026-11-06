package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.support.ConcurrentRunner;
import com.cartmate.cartmate.support.ConcurrentRunner.Results;
import com.cartmate.cartmate.support.TestEventRecorder;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.address;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedLine;
import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class SharedGroupOrderConcurrencyTest {

    private static final int ROUNDS = 20;
    private static final int THREADS = 8;

    @Autowired
    private SharedGroupOrderFacade facade;

    @Autowired
    private SharedGroupOrderRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestEventRecorder eventRecorder;

    private final long base = 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @BeforeEach
    void setUp() {
        eventRecorder.clear();
    }

    @Test
    @DisplayName("마지막 결제와 기한 만료 취소가 동시에 와도 확정 또는 취소 중 하나로만 끝난다")
    void lastPaymentVersusDeadlineCancel() throws InterruptedException {
        LocalDateTime closedAt = expiredClosedAt();
        int confirmedRounds = 0;
        int canceledRounds = 0;
        for (int round = 0; round < ROUNDS; round++) {
            long creator = nextId();
            long member = nextId();
            long last = nextId();
            Long orderId = saveOrder(closedAt, creator, List.of(creator, member, last), List.of(creator, member));
            LocalDateTime paidAt = closedAt.plus(SharedGroupOrder.PAYMENT_PERIOD).minusMinutes(1);

            Results results = ConcurrentRunner.run(
                    () -> facade.applyPayment(orderId, last, last, paidAt, null),
                    () -> facade.cancelByDeadline(orderId));
            results.assertNoUnexpected(ServiceException.class);
            String paymentResult = results.succeeded(0) ? "PAID" : ((ServiceException) results.error(0)).getResultCode();

            SharedGroupOrder order = findOrder(orderId);
            long confirmedEvents = eventRecorder.count(SharedGroupOrderConfirmedEvent.class, e -> e.sharedGroupOrderId().equals(orderId));
            long canceledEvents = eventRecorder.count(SharedGroupOrderCanceledEvent.class, e -> e.sharedGroupOrderId().equals(orderId));
            assertThat(results.error(1)).as("기한 만료 취소는 예외 없이 끝난다").isNull();
            if (order.getStatus() == SharedGroupOrderStatus.CONFIRMED) {
                confirmedRounds++;
                assertThat(paymentResult).isEqualTo("PAID");
                assertThat(confirmedEvents).isEqualTo(1);
                assertThat(canceledEvents).isZero();
                assertThat(order.getCancelReason()).isNull();
                assertThat(order.getCanceledAt()).isNull();
                assertThat(paidStatusOf(order)).containsOnly(true);
            } else {
                canceledRounds++;
                assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
                assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.DEADLINE_EXPIRED);
                assertThat(order.getConfirmedAt()).isNull();
                assertThat(paymentResult).isEqualTo(OrderErrorCode.ORDER_NOT_PAYABLE.resultCode());
                assertThat(canceledEvents).isEqualTo(1);
                assertThat(confirmedEvents).isZero();
                assertThat(order.getParticipant(last).isPaid()).isFalse();
            }
        }
        assertThat(confirmedRounds + canceledRounds).isEqualTo(ROUNDS);
    }

    @Test
    @DisplayName("마지막 두 참여자가 동시에 결제해도 주문은 한 번만 확정되고 확정 이벤트도 한 번만 나간다")
    void lastTwoParticipantsPaySimultaneously() throws InterruptedException {
        LocalDateTime closedAt = LocalDateTime.now();
        for (int round = 0; round < ROUNDS; round++) {
            long creator = nextId();
            long first = nextId();
            long second = nextId();
            Long orderId = saveOrder(closedAt, creator, List.of(creator, first, second), List.of(creator));

            Results results = ConcurrentRunner.run(
                    () -> facade.applyPayment(orderId, first, first, LocalDateTime.now(), null),
                    () -> facade.applyPayment(orderId, second, second, LocalDateTime.now(), null));

            results.assertNoUnexpected();
            SharedGroupOrder order = findOrder(orderId);
            assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CONFIRMED);
            assertThat(order.getParticipant(first).isPaid()).isTrue();
            assertThat(order.getParticipant(second).isPaid()).isTrue();
            assertThat(eventRecorder.count(SharedGroupOrderConfirmedEvent.class, e -> e.sharedGroupOrderId().equals(orderId))).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("서버 여러 대의 스케줄러가 같은 공동 주문을 동시에 기한 만료 취소해도 취소 이벤트는 한 번만 나간다")
    void concurrentDeadlineCancel() throws InterruptedException {
        long creator = nextId();
        long member = nextId();
        Long orderId = saveOrder(expiredClosedAt(), creator, List.of(creator, member), List.of());

        ConcurrentRunner.run(THREADS, i -> facade.cancelByDeadline(orderId)).assertNoUnexpected();

        assertThat(eventRecorder.count(SharedGroupOrderCanceledEvent.class, e -> e.sharedGroupOrderId().equals(orderId))).isEqualTo(1);
        assertThat(findOrder(orderId).getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
    }

    private LocalDateTime expiredClosedAt() {
        return LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(5);
    }

    private long nextId() {
        return base + sequence++;
    }

    private Long saveOrder(LocalDateTime closedAt, Long creator, List<Long> participants, List<Long> paidMembers) {
        return transactionTemplate.execute(status -> {
            SharedGroupOrder order = SharedGroupOrder.create(nextId(), creator, participants,
                    List.of(sharedLine(2L, 4L, 1L, 1, 30000L)), closedAt);
            order.markStockReserved();
            LocalDateTime paidAt = closedAt.plusMinutes(1);
            paidMembers.forEach(memberId -> order.pay(memberId, memberId, paidAt,
                    order.isCreator(memberId) ? address() : null));
            return repository.save(order).getId();
        });
    }

    private SharedGroupOrder findOrder(Long orderId) {
        return transactionTemplate.execute(status -> {
            SharedGroupOrder order = repository.findById(orderId).orElseThrow();
            order.getParticipants().size();
            return order;
        });
    }

    private List<Boolean> paidStatusOf(SharedGroupOrder order) {
        return order.getParticipants().stream().map(SharedGroupOrderParticipant::isPaid).toList();
    }
}
