package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderCancelReason;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@Import(OrderConcurrencyTest.OrderEventRecorder.class)
@ActiveProfiles("test")
class OrderConcurrencyTest {

    private static final int ROUNDS = 20;

    @Autowired
    private OrderFacade facade;

    @Autowired
    private OrderRepository repository;

    @Autowired
    private OrderEventRecorder eventRecorder;

    private final List<Long> createdIds = new ArrayList<>();

    private final long base = 9_500_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @BeforeEach
    void setUp() {
        eventRecorder.clear();
    }

    @AfterEach
    void tearDown() {
        createdIds.forEach(repository::deleteById);
    }

    @Test
    @DisplayName("기한 안에 한 결제와 기한 만료 취소가 동시에 와도 결제 완료 또는 취소 중 하나로만 끝난다")
    void paymentVersusDeadlineCancel() throws InterruptedException {
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minus(Order.PAYMENT_PERIOD).minusMinutes(5);
        LocalDateTime paidAt = createdAt.plus(Order.PAYMENT_PERIOD).minusMinutes(1);
        int paidRounds = 0;
        int canceledRounds = 0;
        for (int round = 0; round < ROUNDS; round++) {
            long buyer = nextId();
            Long orderId = saveOrder(buyer, createdAt);

            AtomicReference<String> paymentResult = new AtomicReference<>();
            AtomicReference<Throwable> cancelError = new AtomicReference<>();
            runConcurrently(List.of(
                    () -> {
                        try {
                            facade.applyPayment(orderId, buyer, paidAt);
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

            Order order = repository.findById(orderId).orElseThrow();
            long paidEvents = eventRecorder.paidCountFor(orderId);
            long canceledEvents = eventRecorder.canceledCountFor(orderId);
            assertThat(cancelError.get()).isNull();
            if (order.getStatus() == OrderStatus.PAID) {
                paidRounds++;
                assertThat(paymentResult.get()).isEqualTo("PAID");
                assertThat(order.getPaidAt()).isEqualTo(paidAt);
                assertThat(paidEvents).isEqualTo(1);
                assertThat(canceledEvents).isZero();
            } else {
                canceledRounds++;
                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
                assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.PAYMENT_EXPIRED);
                assertThat(order.getPaidAt()).isNull();
                assertThat(paymentResult.get()).isEqualTo(OrderErrorCode.ORDER_NOT_PAYMENT_PENDING.resultCode());
                assertThat(canceledEvents).isEqualTo(1);
                assertThat(paidEvents).isZero();
            }
        }
        assertThat(paidRounds + canceledRounds).isEqualTo(ROUNDS);
    }

    private long nextId() {
        return base + sequence++;
    }

    private Long saveOrder(Long buyer, LocalDateTime createdAt) {
        Order order = Order.createPersonal("IT-" + nextId(), buyer,
                List.of(new OrderLine(2L, 4L, null, "축구공", "5호", 25000, 1)),
                DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"), createdAt);
        Long orderId = repository.save(order).getId();
        createdIds.add(orderId);
        return orderId;
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

    @TestConfiguration
    static class OrderEventRecorder {

        private final Queue<Long> paidIds = new ConcurrentLinkedQueue<>();
        private final Queue<Long> canceledIds = new ConcurrentLinkedQueue<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(OrderPaidEvent event) {
            paidIds.add(event.orderId());
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(OrderCanceledEvent event) {
            canceledIds.add(event.orderId());
        }

        long paidCountFor(Long id) {
            return paidIds.stream().filter(id::equals).count();
        }

        long canceledCountFor(Long id) {
            return canceledIds.stream().filter(id::equals).count();
        }

        void clear() {
            paidIds.clear();
            canceledIds.clear();
        }
    }
}
