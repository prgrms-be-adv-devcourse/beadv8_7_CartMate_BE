package com.cartmate.cartmate.boundedContext.delivery.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryClaimItemTest {

    @Test
    @DisplayName("클레임 상품을 생성하면 클레임, 배송상품, 요청 수량이 저장된다")
    void create() {
        Delivery delivery = Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        DeliveryItem deliveryItem = DeliveryItem.create(delivery, 1000L, "티셔츠", 2L);
        DeliveryClaim claim = DeliveryClaim.create(
                delivery, 1L, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다");

        DeliveryClaimItem claimItem = DeliveryClaimItem.create(claim, deliveryItem, 1L);

        assertThat(claimItem.getClaim()).isSameAs(claim);
        assertThat(claimItem.getDeliveryItem()).isSameAs(deliveryItem);
        assertThat(claimItem.getQuantity()).isEqualTo(1L);
    }
}
