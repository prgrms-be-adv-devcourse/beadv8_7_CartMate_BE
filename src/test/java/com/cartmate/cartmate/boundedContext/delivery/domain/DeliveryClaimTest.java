package com.cartmate.cartmate.boundedContext.delivery.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryClaimTest {

    @Test
    @DisplayName("클레임을 생성하면 접수 상태로 시작하고 입력한 값이 저장된다")
    void createStartsAsRequested() {
        Delivery delivery = Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");

        DeliveryClaim claim = DeliveryClaim.create(
                delivery, 1L, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(claim.getDelivery()).isSameAs(delivery);
        assertThat(claim.getMemberId()).isEqualTo(1L);
        assertThat(claim.getClaimType()).isEqualTo(ClaimType.EXCHANGE);
        assertThat(claim.getReason()).isEqualTo(ClaimReason.SIZE_MISMATCH);
        assertThat(claim.getDetail()).isEqualTo("L로 교환 원합니다");
        assertThat(claim.getProcessedAt()).isNull();
    }
}
