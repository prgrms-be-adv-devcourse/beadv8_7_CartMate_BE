package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryBuyerCancelTest {

    private static final LocalDateTime BEFORE_PREPARE = LocalDateTime.now().minusMinutes(1);
    private static final LocalDateTime AFTER_PREPARE = LocalDateTime.now().plusMinutes(1);

    private Delivery delivery() {
        return Delivery.create(7L, 1007L, 100L, "O-100", PurchaseType.INDIVIDUAL_GROUP,
                "수령인", "010", "00000", "서울", "101호", "key-100", "hash");
    }

    @Test
    @DisplayName("판매자가 확인하기 전이면 취소할 수 있다")
    void requestedIsAllowed() {
        assertThat(delivery().buyerCancelVerdict(BEFORE_PREPARE)).isEqualTo(BuyerCancelVerdict.ALLOWED);
    }

    @Test
    @DisplayName("판매자가 요청 뒤에 확인했고 아직 발송 전이면 취소할 수 있다")
    void preparedAfterRequestIsAllowed() {
        Delivery delivery = delivery();
        delivery.prepare("CJ", "123");

        assertThat(delivery.buyerCancelVerdict(BEFORE_PREPARE)).isEqualTo(BuyerCancelVerdict.ALLOWED);

        delivery.cancelByBuyerRequest(BEFORE_PREPARE, LocalDateTime.now());
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
    }

    @Test
    @DisplayName("판매자가 요청 전에 이미 확인했으면 취소할 수 없다")
    void preparedBeforeRequestIsNotCancelable() {
        Delivery delivery = delivery();
        delivery.prepare("CJ", "123");

        assertThat(delivery.buyerCancelVerdict(AFTER_PREPARE)).isEqualTo(BuyerCancelVerdict.NOT_CANCELABLE);
        assertThatThrownBy(() -> delivery.cancelByBuyerRequest(AFTER_PREPARE, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
    }

    @Test
    @DisplayName("판매자가 요청 뒤에 확인했지만 이미 발송했으면 처리 지연으로 거절한다")
    void shippedAfterRequestIsDelayed() {
        Delivery delivery = delivery();
        delivery.prepare("CJ", "123");
        delivery.ship();

        assertThat(delivery.buyerCancelVerdict(BEFORE_PREPARE)).isEqualTo(BuyerCancelVerdict.SHIPPED_AFTER_REQUEST);
        assertThat(BuyerCancelVerdict.SHIPPED_AFTER_REQUEST.getRejectCode().resultCode())
                .contains("DELIVERY_CANCEL_DELAYED");
    }

    @Test
    @DisplayName("이미 취소된 배송은 다시 취소해도 그대로다")
    void canceledIsIdempotent() {
        Delivery delivery = delivery();
        delivery.cancelByBuyerRequest(BEFORE_PREPARE, LocalDateTime.now());
        delivery.cancelByBuyerRequest(BEFORE_PREPARE, LocalDateTime.now());

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
        assertThat(delivery.buyerCancelVerdict(BEFORE_PREPARE)).isEqualTo(BuyerCancelVerdict.ALLOWED);
    }
}
