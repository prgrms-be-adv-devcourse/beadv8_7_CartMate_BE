package com.cartmate.cartmate.boundedContext.delivery.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {

    @Test
    @DisplayName("배송을 생성하면 배송요청 상태로 시작한다")
    void createStartsAsRequested() {
        Delivery delivery = Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }
}
