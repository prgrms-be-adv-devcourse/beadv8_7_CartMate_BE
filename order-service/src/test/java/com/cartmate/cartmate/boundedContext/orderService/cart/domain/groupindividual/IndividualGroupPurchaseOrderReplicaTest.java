package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplica.Line;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus.CANCELED;
import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus.PAID;
import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

class IndividualGroupPurchaseOrderReplicaTest {

    private static final Long ORDER_ID = 100L;
    private static final Long PURCHASE_ID = 10L;
    private static final Long BUYER = 1L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 2, 12, 0);
    private static final LocalDateTime PAID_AT = CREATED_AT.plusMinutes(5);
    private static final List<Line> LINES = List.of(new Line(1000L, 7L, 70L, 3L, 2, 25000L), new Line(1001L, 8L, 80L, 4L, 1, 15000L));

    @Test
    @DisplayName("생성 → 결제 → 취소 순으로 오면 결제 대기 → 결제 완료 → 취소가 된다")
    void inOrder() {
        IndividualGroupPurchaseOrderReplica replica = IndividualGroupPurchaseOrderReplica.create(ORDER_ID);

        replica.applyCreated(PURCHASE_ID, BUYER, CREATED_AT, LINES);
        assertThat(replica.getStatus()).isEqualTo(PENDING);
        assertThat(replica.getOrderedAt()).isEqualTo(CREATED_AT);
        assertThat(replica.getLines()).extracting(IndividualGroupPurchaseOrderLineReplica::getOrderItemId)
                .containsExactly(1000L, 1001L);

        replica.applyPaid(PURCHASE_ID, BUYER, PAID_AT, LINES);
        assertThat(replica.getStatus()).isEqualTo(PAID);
        assertThat(replica.getOrderedAt()).isEqualTo(CREATED_AT);

        replica.applyCanceled();
        assertThat(replica.getStatus()).isEqualTo(CANCELED);
    }

    @Test
    @DisplayName("결제가 생성보다 먼저 오면 결제 완료로 만들고, 늦게 온 생성은 상태를 되돌리지 않고 주문 시각만 바로잡는다")
    void paidBeforeCreated() {
        IndividualGroupPurchaseOrderReplica replica = IndividualGroupPurchaseOrderReplica.create(ORDER_ID);

        replica.applyPaid(PURCHASE_ID, BUYER, PAID_AT, LINES);
        assertThat(replica.getStatus()).isEqualTo(PAID);
        assertThat(replica.getOrderedAt()).isEqualTo(PAID_AT);

        replica.applyCreated(PURCHASE_ID, BUYER, CREATED_AT, LINES);
        assertThat(replica.getStatus()).isEqualTo(PAID);
        assertThat(replica.getOrderedAt()).isEqualTo(CREATED_AT);
        assertThat(replica.getLines()).hasSize(2);
    }

    @Test
    @DisplayName("취소가 먼저 오면 공동구매를 모른 채 취소로 남고, 늦게 온 생성 · 결제는 정보만 채우고 취소를 풀지 않는다")
    void canceledFirst() {
        IndividualGroupPurchaseOrderReplica replica = IndividualGroupPurchaseOrderReplica.create(ORDER_ID);

        replica.applyCanceled();
        assertThat(replica.getStatus()).isEqualTo(CANCELED);
        assertThat(replica.getIndividualGroupPurchaseId()).isNull();

        replica.applyCreated(PURCHASE_ID, BUYER, CREATED_AT, LINES);
        replica.applyPaid(PURCHASE_ID, BUYER, PAID_AT, LINES);

        assertThat(replica.getStatus()).isEqualTo(CANCELED);
        assertThat(replica.getIndividualGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(replica.getBuyerMemberId()).isEqualTo(BUYER);
    }

    @Test
    @DisplayName("같은 이벤트가 두 번 와도 항목이 늘지 않고 결과가 같다")
    void duplicates() {
        IndividualGroupPurchaseOrderReplica replica = IndividualGroupPurchaseOrderReplica.create(ORDER_ID);

        replica.applyCreated(PURCHASE_ID, BUYER, CREATED_AT, LINES);
        replica.applyCreated(PURCHASE_ID, BUYER, CREATED_AT, LINES);
        replica.applyPaid(PURCHASE_ID, BUYER, PAID_AT, LINES);
        replica.applyPaid(PURCHASE_ID, BUYER, PAID_AT, LINES);

        assertThat(replica.getStatus()).isEqualTo(PAID);
        assertThat(replica.getLines()).hasSize(2);
    }

    @Test
    @DisplayName("판매 중지로 한 판매자 항목만 취소되면 그 항목만 취소 표시, 남은 항목이 없으면 주문도 취소가 된다")
    void sellerItemsCanceled() {
        IndividualGroupPurchaseOrderReplica replica = IndividualGroupPurchaseOrderReplica.create(ORDER_ID);
        replica.applyCreated(PURCHASE_ID, BUYER, CREATED_AT, LINES);
        replica.applyPaid(PURCHASE_ID, BUYER, PAID_AT, LINES);

        replica.applySellerItemsCanceled(3L);
        assertThat(replica.getStatus()).isEqualTo(PAID);
        assertThat(replica.getLines()).extracting(IndividualGroupPurchaseOrderLineReplica::isCanceled)
                .containsExactly(true, false);

        replica.applySellerItemsCanceled(3L);
        replica.applySellerItemsCanceled(4L);
        assertThat(replica.getStatus()).isEqualTo(CANCELED);
    }
}
