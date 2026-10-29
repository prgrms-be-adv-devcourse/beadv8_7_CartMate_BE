package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseStatus;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@Import(SharedGroupPurchaseConcurrencyTest.ClosedEventRecorder.class)
@ActiveProfiles("test")
class SharedGroupPurchaseConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private SharedGroupPurchaseFacade facade;

    @Autowired
    private SharedGroupPurchaseRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ClosedEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdIds = new ArrayList<>();

    private final long creator = 7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 1_000;

    @BeforeEach
    void setUp() {
        eventRecorder.clear();
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            new TestDataCleaner(jdbcTemplate).deleteSharedGroupOrdersOfPurchases(createdIds);
            createdIds.forEach(repository::deleteById);
        });
    }

    @Test
    @DisplayName("마지막 한 자리에 여러 명이 동시에 참여해도 정원을 넘지 않는다")
    void lastSeatJoin() throws InterruptedException {
        var created = facade.create(creator, new SharedGroupPurchaseCreateRequest("동시 참여", 3));
        createdIds.add(created.id());
        facade.join(creator + 1, created.inviteCode());

        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        runConcurrently(index -> {
            try {
                facade.join(creator + 100 + index, created.inviteCode());
                success.incrementAndGet();
            } catch (ServiceException e) {
                if (e.getResultCode().equals(CartErrorCode.CART_SHARED_FULL.resultCode())) {
                    full.incrementAndGet();
                }
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(full.get()).isEqualTo(THREADS - 1);
        int memberCount = transactionTemplate.execute(status -> repository.findById(created.id()).orElseThrow().getMembers().size());
        assertThat(memberCount).isEqualTo(3);
    }

    @Test
    @DisplayName("여러 명이 동시에 수량을 1씩 늘려도 증가분이 모두 반영된다")
    void concurrentQuantityAdjust() throws InterruptedException {
        var created = facade.create(creator, new SharedGroupPurchaseCreateRequest("동시 수량", 5));
        createdIds.add(created.id());
        facade.addItem(creator, created.id(), 4L, 10);
        Long itemId = transactionTemplate.execute(status ->
                repository.findById(created.id()).orElseThrow().getItems().getFirst().getId());

        runConcurrently(index -> facade.adjustItemQuantity(creator, created.id(), itemId, 1));

        int quantity = transactionTemplate.execute(status ->
                repository.findById(created.id()).orElseThrow().getItems().getFirst().getQuantity());
        assertThat(quantity).isEqualTo(10 + THREADS);
    }

    @Test
    @DisplayName("생성자의 마감 요청이 동시에 여러 번 와도 마감과 마감 이벤트는 한 번만 일어난다")
    void concurrentCreatorClose() throws InterruptedException {
        var created = facade.create(creator, new SharedGroupPurchaseCreateRequest("동시 마감", 5));
        createdIds.add(created.id());
        facade.addItem(creator, created.id(), 4L, 2);

        AtomicInteger success = new AtomicInteger();
        runConcurrently(index -> {
            try {
                facade.close(creator, created.id());
                success.incrementAndGet();
            } catch (ServiceException ignored) {
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(eventRecorder.countFor(created.id())).isEqualTo(1);
        assertThat(statusOf(created.id())).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
    }

    @Test
    @DisplayName("서버 여러 대의 스케줄러가 같은 공동구매를 동시에 자동 마감해도 이벤트는 한 번만 나간다")
    void concurrentDeadlineClose() throws InterruptedException {
        LocalDateTime past = LocalDateTime.now().minusHours(49);
        Long id = transactionTemplate.execute(status -> {
            SharedGroupPurchase purchase = SharedGroupPurchase.create("기한 지남", creator, 5, "deadlinetest", past);
            purchase.addItem(creator, 2L, 4L, 1, past);
            return repository.save(purchase).getId();
        });
        createdIds.add(id);

        runConcurrently(index -> facade.closeByDeadline(id));

        assertThat(eventRecorder.countFor(id)).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
    }

    private SharedGroupPurchaseStatus statusOf(Long id) {
        return repository.findById(id).orElseThrow().getStatus();
    }


    private void runConcurrently(IndexedTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run(index);
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

    @FunctionalInterface
    private interface IndexedTask {
        void run(int index);
    }


    @TestConfiguration
    static class ClosedEventRecorder {

        private final Queue<Long> closedIds = new ConcurrentLinkedQueue<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(SharedGroupPurchaseClosedEvent event) {
            closedIds.add(event.sharedGroupPurchaseId());
        }

        long countFor(Long id) {
            return closedIds.stream().filter(id::equals).count();
        }

        void clear() {
            closedIds.clear();
        }
    }
}
