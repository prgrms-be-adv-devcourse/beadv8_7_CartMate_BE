package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.support.TestDataCleaner;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class SharedGroupOrderCreateIntegrationTest {

    @Autowired
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    private final List<Long> purchaseIds = new ArrayList<>();

    private final long base = 6_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private final Long creator = base;
    private final Long member = base + 1;

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            new TestDataCleaner(jdbcTemplate).deleteSharedGroupOrdersOfPurchases(purchaseIds);
            purchaseIds.forEach(sharedGroupPurchaseRepository::deleteById);
        });
    }

    @Test
    @DisplayName("공동구매를 마감하면 아웃박스 릴레이 뒤 공동 주문이 만들어지고 참여자가 조회할 수 있다")
    void closeCreatesOrder() {
        var created = sharedGroupPurchaseFacade.create(creator, new SharedGroupPurchaseCreateRequest("주문 생성 통합", 5));
        purchaseIds.add(created.id());
        sharedGroupPurchaseFacade.join(member, created.inviteCode());
        sharedGroupPurchaseFacade.addItem(member, created.id(), 4L, 3);

        sharedGroupPurchaseFacade.close(creator, created.id());
        relayUntilOrderCreated(created.id());

        List<SharedGroupOrderSummaryResponse> myOrders = sharedGroupOrderFacade.getMyList(member);
        SharedGroupOrderSummaryResponse summary = myOrders.stream()
                .filter(order -> order.sharedGroupPurchaseId().equals(created.id()))
                .findFirst()
                .orElseThrow();
        assertThat(summary.totalAmount()).isEqualTo(75000L);
        assertThat(summary.myShareAmount()).isEqualTo(37500L);
        assertThat(summary.participantCount()).isEqualTo(2);

        SharedGroupOrderDetailResponse detail = sharedGroupOrderFacade.getDetail(creator, summary.id());
        assertThat(detail.creator()).isTrue();
        assertThat(detail.items()).singleElement()
                .satisfies(item -> assertThat(item.unitPrice()).isEqualTo(25000L));
    }

    // 마감 트랜잭션은 CLOSED + 아웃박스까지만. 공동 주문은 릴레이가 넘긴 마감 이벤트를 order consumer가 받아 만든다
    private void relayUntilOrderCreated(Long purchaseId) {
        for (int i = 0; i < 20; i++) {
            ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
            outboxRelay.relay();
            Integer orders = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared_group_order WHERE shared_group_purchase_id = ?", Integer.class, purchaseId);
            if (orders > 0) {
                return;
            }
        }
        throw new AssertionError("공동 주문이 만들어지지 않았다: sharedGroupPurchaseId=" + purchaseId);
    }
}
