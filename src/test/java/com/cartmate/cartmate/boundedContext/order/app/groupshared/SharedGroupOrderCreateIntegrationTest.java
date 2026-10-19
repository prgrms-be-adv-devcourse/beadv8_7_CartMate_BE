package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SharedGroupOrderCreateIntegrationTest {

    private static final Long CREATOR = 7_000_001L;
    private static final Long MEMBER = 7_000_002L;

    @Autowired
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    private final List<Long> purchaseIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        purchaseIds.forEach(purchaseId -> {
            sharedGroupOrderRepository.findAll().stream()
                    .filter(order -> order.getSharedGroupPurchaseId().equals(purchaseId))
                    .forEach(sharedGroupOrderRepository::delete);
            sharedGroupPurchaseRepository.deleteById(purchaseId);
        });
    }

    @Test
    @DisplayName("공동구매를 마감하면 같은 트랜잭션에서 공동 주문이 만들어지고 참여자가 조회할 수 있다")
    void closeCreatesOrder() {
        var created = sharedGroupPurchaseFacade.create(CREATOR, new SharedGroupPurchaseCreateRequest("주문 생성 통합", 5));
        purchaseIds.add(created.id());
        sharedGroupPurchaseFacade.join(MEMBER, created.inviteCode());
        sharedGroupPurchaseFacade.addItem(MEMBER, created.id(), 4L, 3);

        sharedGroupPurchaseFacade.close(CREATOR, created.id());

        List<SharedGroupOrderSummaryResponse> myOrders = sharedGroupOrderFacade.getMyList(MEMBER);
        SharedGroupOrderSummaryResponse summary = myOrders.stream()
                .filter(order -> order.sharedGroupPurchaseId().equals(created.id()))
                .findFirst()
                .orElseThrow();
        assertThat(summary.totalAmount()).isEqualTo(75000L);
        assertThat(summary.myShareAmount()).isEqualTo(37500L);
        assertThat(summary.participantCount()).isEqualTo(2);

        SharedGroupOrderDetailResponse detail = sharedGroupOrderFacade.getDetail(CREATOR, summary.id());
        assertThat(detail.creator()).isTrue();
        assertThat(detail.items()).singleElement()
                .satisfies(item -> assertThat(item.unitPrice()).isEqualTo(25000L));
    }
}
