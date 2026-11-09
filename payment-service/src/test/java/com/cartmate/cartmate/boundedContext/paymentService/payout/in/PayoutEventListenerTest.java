package com.cartmate.cartmate.boundedContext.paymentService.payout.in;

import com.cartmate.cartmate.boundedContext.paymentService.payout.app.PayoutFacade;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PayoutEventListenerTest {

    private final PayoutFacade payoutFacade = mock(PayoutFacade.class);
    private final PayoutEventListener listener = new PayoutEventListener(payoutFacade);

    private final DeliveryConfirmedEvent event = new DeliveryConfirmedEvent(
            3L, 5L, 100L, PurchaseType.INDIVIDUAL_GROUP,
            LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 8, 10, 0),
            List.of(new DeliveryConfirmedEvent.Item(11L, "상품", 1_000L, 2L)));

    @Test
    @DisplayName("구매확정 이벤트를 받으면 정산을 만든다")
    void createsPayoutsOnDeliveryConfirmed() {
        listener.handle(event);

        verify(payoutFacade).createPayouts(event);
    }

    @Test
    @DisplayName("정산 생성이 실패하면 예외를 올려 인박스가 다시 시도하게 한다")
    void rethrowsWhenPayoutCreationFails() {
        doThrow(new IllegalStateException("db down")).when(payoutFacade).createPayouts(event);

        assertThatThrownBy(() -> listener.handle(event)).isInstanceOf(IllegalStateException.class);
    }
}
