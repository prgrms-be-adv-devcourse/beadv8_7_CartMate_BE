package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

            AtomicReference<String> paymentResult = new AtomicReference<>();
            AtomicReference<Throwable> cancelError = new AtomicReference<>();
            runConcurrently(List.of(
                    () -> {
                        try {
                            facade.applyPayment(orderId, last, last, paidAt, null);
                            paymentResult.set("PAID");
                        } catch (ServiceException e) {
                            paymentResult.set(e.getResultCode());
                        }
                    },
                    () -> {
                        try {
                            facade.cancelByDeadline(orderId);
                        } catch (RuntimeException e) {
                            cancelError.set(e);
                        }
                    }
            ));

            SharedGroupOrder order = findOrder(orderId);
            long confirmedEvents = eventRecorder.count(SharedGroupOrderConfirmedEvent.class, e -> e.sharedGroupOrderId().equals(orderId));
            long canceledEvents = eventRecorder.count(SharedGroupOrderCanceledEvent.class, e -> e.sharedGroupOrderId().equals(orderId));
            assertThat(cancelError.get()).isNull();
            if (order.getStatus() == SharedGroupOrderStatus.CONFIRMED) {
                confirmedRounds++;
                assertThat(paymentResult.get()).isEqualTo("PAID");
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
                assertThat(paymentResult.get()).isEqualTo(OrderErrorCode.ORDER_NOT_PAYABLE.resultCode());
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

            AtomicInteger success = new AtomicInteger();
            runConcurrently(List.of(
                    () -> {
                        facade.applyPayment(orderId, first, first, LocalDateTime.now(), null);
                        success.incrementAndGet();
                    },
                    () -> {
                        facade.applyPayment(orderId, second, second, LocalDateTime.now(), null);
                        success.incrementAndGet();
                    }
            ));

            SharedGroupOrder order = findOrder(orderId);
            assertThat(success.get()).isEqualTo(2);
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

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> facade.cancelByDeadline(orderId));
        }
        runConcurrently(tasks);

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
                    List.of(new OrderItemLine(2L, 4L, 1L, "상품", null, 1, 30000L)), closedAt);
            order.markStockReserved();
            LocalDateTime paidAt = closedAt.plusMinutes(1);
            paidMembers.forEach(memberId -> order.pay(memberId, memberId, paidAt,
                    order.isCreator(memberId) ? DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호") : null));
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

    private void runConcurrently(List<Runnable> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        for (Runnable task : tasks) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }
}
