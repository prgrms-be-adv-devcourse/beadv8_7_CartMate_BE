package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverySellerReasonCancelTest {

    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 11, 11, 10, 0);

    private Delivery delivery() {
        return Delivery.create(7L, 1007L, 100L, "O-100", PurchaseType.INDIVIDUAL_GROUP,
                "수령인", "010", "00000", "서울", "101호", "key-100", "hash");
    }

    @Test
    @DisplayName("배송요청이면 판매자가 고른 사유로 취소한다")
    void requestedIsCanceled() {
        Delivery delivery = delivery();

        boolean canceled = delivery.cancelBySeller(DeliveryCancelReason.SELLER_OUT_OF_STOCK, CANCELED_AT);

        assertThat(canceled).isTrue();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
        assertThat(delivery.getCanceledAt()).isEqualTo(CANCELED_AT);
        assertThat(delivery.getCancelReason()).isEqualTo(DeliveryCancelReason.SELLER_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("배송준비중도 취소하고 송장 정보는 남긴다")
    void preparingIsCanceledAndKeepsTracking() {
        Delivery delivery = delivery();
        delivery.prepare("CJ", "123");

        assertThat(delivery.cancelBySeller(DeliveryCancelReason.SELLER_PRODUCT_ISSUE, CANCELED_AT)).isTrue();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
        assertThat(delivery.getCarrier()).isEqualTo("CJ");
        assertThat(delivery.getTrackingNumber()).isEqualTo("123");
    }

    @Test
    @DisplayName("같은 사유로 다시 누르면 아무것도 바꾸지 않고 성공한다")
    void sameReasonAgainIsNoop() {
        Delivery delivery = delivery();
        delivery.cancelBySeller(DeliveryCancelReason.SELLER_OUT_OF_STOCK, CANCELED_AT);

        boolean canceled = delivery.cancelBySeller(DeliveryCancelReason.SELLER_OUT_OF_STOCK, CANCELED_AT.plusMinutes(1));

        assertThat(canceled).isFalse();
        assertThat(delivery.getCanceledAt()).isEqualTo(CANCELED_AT);
    }

    @Test
    @DisplayName("다른 사유로 다시 누르면 처음 사유를 담아 이미 취소됐다고 알린다")
    void differentReasonIsRejectedWithFirstReason() {
        Delivery delivery = delivery();
        delivery.cancelBySeller(DeliveryCancelReason.SELLER_OUT_OF_STOCK, CANCELED_AT);

        assertThatThrownBy(() -> delivery.cancelBySeller(DeliveryCancelReason.SELLER_OTHER, CANCELED_AT))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_ALREADY_CANCELED")
                .hasMessageContaining("(사유: 품절·재고 부족)");
        assertThat(delivery.getCancelReason()).isEqualTo(DeliveryCancelReason.SELLER_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("구매자 취소로 이미 취소된 배송은 그 사유를 담아 알린다")
    void buyerCanceledIsRejected() {
        Delivery delivery = delivery();
        delivery.cancelByBuyerRequest(LocalDateTime.now(), CANCELED_AT);

        assertThat(delivery.getCancelReason()).isEqualTo(DeliveryCancelReason.BUYER_CANCELED);
        assertThatThrownBy(() -> delivery.cancelBySeller(DeliveryCancelReason.SELLER_OUT_OF_STOCK, CANCELED_AT))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_ALREADY_CANCELED")
                .hasMessageContaining("(사유: 구매자 취소)");
    }

    @Test
    @DisplayName("판매글 내림으로 취소되면 판매 중지 사유가 남는다")
    void postHiddenRecordsSalesStopped() {
        Delivery delivery = delivery();
        delivery.cancelByPostHidden(CANCELED_AT);

        assertThat(delivery.getCancelReason()).isEqualTo(DeliveryCancelReason.SALES_STOPPED);
        assertThatThrownBy(() -> delivery.cancelBySeller(DeliveryCancelReason.SELLER_OTHER, CANCELED_AT))
                .hasMessageContaining("(사유: 판매 중지)");
    }

    @Test
    @DisplayName("발송한 뒤에는 판매자가 취소할 수 없다")
    void shippedIsNotCancelable() {
        Delivery delivery = delivery();
        delivery.prepare("CJ", "123");
        delivery.ship();

        assertThatThrownBy(() -> delivery.cancelBySeller(DeliveryCancelReason.SELLER_OUT_OF_STOCK, CANCELED_AT))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_NOT_CANCELABLE_BY_SELLER");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    @DisplayName("판매자가 고를 수 없는 사유나 빈 사유는 받지 않는다")
    void invalidReasonIsRejected() {
        Delivery delivery = delivery();

        assertThatThrownBy(() -> delivery.cancelBySeller(DeliveryCancelReason.BUYER_CANCELED, CANCELED_AT))
                .hasMessageContaining("DELIVERY_CANCEL_REASON_NOT_ALLOWED");
        assertThatThrownBy(() -> delivery.cancelBySeller(DeliveryCancelReason.SALES_STOPPED, CANCELED_AT))
                .hasMessageContaining("DELIVERY_CANCEL_REASON_NOT_ALLOWED");
        assertThatThrownBy(() -> delivery.cancelBySeller(null, CANCELED_AT))
                .hasMessageContaining("DELIVERY_CANCEL_REASON_REQUIRED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }

    @Test
    @DisplayName("취소된 배송에 배송준비·발송을 누르면 사유를 담아 이미 취소됐다고 알린다")
    void prepareAndShipOnCanceledTellReason() {
        Delivery requested = delivery();
        requested.cancelByBuyerRequest(LocalDateTime.now(), CANCELED_AT);
        assertThatThrownBy(() -> requested.prepare("CJ", "123"))
                .hasMessageContaining("DELIVERY_ALREADY_CANCELED")
                .hasMessageContaining("(사유: 구매자 취소)");

        Delivery preparing = delivery();
        preparing.prepare("CJ", "123");
        preparing.cancelBySeller(DeliveryCancelReason.SELLER_PRODUCT_ISSUE, CANCELED_AT);
        assertThatThrownBy(preparing::ship)
                .hasMessageContaining("DELIVERY_ALREADY_CANCELED")
                .hasMessageContaining("(사유: 상품 문제 발견)");
    }
}
