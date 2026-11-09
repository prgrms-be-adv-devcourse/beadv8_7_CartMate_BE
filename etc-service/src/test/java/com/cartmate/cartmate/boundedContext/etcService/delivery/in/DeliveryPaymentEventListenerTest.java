package com.cartmate.cartmate.boundedContext.etcService.delivery.in;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundCompletedEvent;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeliveryPaymentEventListenerTest {

    private final DeliveryFacade deliveryFacade = mock(DeliveryFacade.class);
    private final DeliveryPaymentEventListener listener = new DeliveryPaymentEventListener(deliveryFacade);

    @Test
    @DisplayName("환불 완료 결과를 받으면 클레임 환불을 완료로 바꾼다")
    void completesClaimRefund() {
        listener.handle(new ClaimRefundCompletedEvent(7L, 100L, 3_000L));

        verify(deliveryFacade).completeClaimRefund(7L);
    }

    @Test
    @DisplayName("환불 실패 결과를 받으면 클레임 환불을 실패로 바꾼다")
    void failsClaimRefund() {
        listener.handle(new ClaimRefundFailedEvent(7L, 100L, "409-PAYMENT_REFUND_EXCEEDS_REMAINING"));

        verify(deliveryFacade).failClaimRefund(7L);
    }

    @Test
    @DisplayName("반영이 실패하면 예외를 올려 인박스가 다시 시도하게 한다")
    void rethrowsWhenReflectionFails() {
        doThrow(new IllegalStateException("db down")).when(deliveryFacade).completeClaimRefund(7L);

        assertThatThrownBy(() -> listener.handle(new ClaimRefundCompletedEvent(7L, 100L, 3_000L)))
                .isInstanceOf(IllegalStateException.class);
    }
}
