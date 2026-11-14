package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.support.ConcurrentRunner;
import com.cartmate.cartmate.support.ConcurrentRunner.Results;
import com.cartmate.cartmate.support.TestEventRecorder;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class SharedGroupPurchaseConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private SharedGroupPurchaseFacade facade;

    @Autowired
    private SharedGroupPurchaseRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestEventRecorder eventRecorder;

    private final long creator = 7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 1_000;

    @BeforeEach
    void setUp() {
        eventRecorder.clear();
    }

    @Test
    @DisplayName("마지막 한 자리에 여러 명이 동시에 참여해도 정원을 넘지 않는다")
    void lastSeatJoin() throws InterruptedException {
        var created = facade.create(creator, new SharedGroupPurchaseCreateRequest("동시 참여", 3));
        facade.join(creator + 1, created.inviteCode());

        Results results = ConcurrentRunner.run(THREADS, index -> facade.join(creator + 100 + index, created.inviteCode()));

        results.assertNoUnexpected(ServiceException.class);
        assertThat(results.successCount()).isEqualTo(1);
        assertThat(results.errorsOf(ServiceException.class)).extracting(ServiceException::getResultCode)
                .hasSize(THREADS - 1)
                .containsOnly(CartErrorCode.CART_SHARED_FULL.resultCode());
        int memberCount = transactionTemplate.execute(status -> repository.findById(created.id()).orElseThrow().getMembers().size());
        assertThat(memberCount).isEqualTo(3);
    }

    @Test
    @DisplayName("여러 명이 동시에 수량을 1씩 늘려도 증가분이 모두 반영된다")
    void concurrentQuantityAdjust() throws InterruptedException {
        var created = facade.create(creator, new SharedGroupPurchaseCreateRequest("동시 수량", 5));
        facade.addItem(creator, created.id(), 4L, 10);
        Long itemId = transactionTemplate.execute(status ->
                repository.findById(created.id()).orElseThrow().getItems().getFirst().getId());

        ConcurrentRunner.run(THREADS, index -> facade.adjustItemQuantity(creator, created.id(), itemId, 1))
                .assertNoUnexpected();

        int quantity = transactionTemplate.execute(status ->
                repository.findById(created.id()).orElseThrow().getItems().getFirst().getQuantity());
        assertThat(quantity).isEqualTo(10 + THREADS);
    }

    @Test
    @DisplayName("생성자의 마감 요청이 동시에 여러 번 와도 마감과 마감 이벤트는 한 번만 일어난다")
    void concurrentCreatorClose() throws InterruptedException {
        var created = facade.create(creator, new SharedGroupPurchaseCreateRequest("동시 마감", 5));
        facade.addItem(creator, created.id(), 4L, 2);

        Results results = ConcurrentRunner.run(THREADS, index -> facade.close(creator, created.id()));

        results.assertNoUnexpected(ServiceException.class);
        assertThat(results.successCount()).isEqualTo(1);
        assertThat(eventRecorder.count(SharedGroupPurchaseClosedEvent.class, e -> e.sharedGroupPurchaseId().equals(created.id()))).isEqualTo(1);
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

        ConcurrentRunner.run(THREADS, index -> facade.closeByDeadline(id)).assertNoUnexpected();

        assertThat(eventRecorder.count(SharedGroupPurchaseClosedEvent.class, e -> e.sharedGroupPurchaseId().equals(id))).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
    }

    private SharedGroupPurchaseStatus statusOf(Long id) {
        return repository.findById(id).orElseThrow().getStatus();
    }
}
