package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
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
    @DisplayName("공동구매를 마감하면 같은 트랜잭션에서 공동 주문이 만들어지고 참여자가 조회할 수 있다")
    void closeCreatesOrder() {
        var created = sharedGroupPurchaseFacade.create(creator, new SharedGroupPurchaseCreateRequest("주문 생성 통합", 5));
        purchaseIds.add(created.id());
        sharedGroupPurchaseFacade.join(member, created.inviteCode());
        sharedGroupPurchaseFacade.addItem(member, created.id(), 4L, 3);

        sharedGroupPurchaseFacade.close(creator, created.id());

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
}
