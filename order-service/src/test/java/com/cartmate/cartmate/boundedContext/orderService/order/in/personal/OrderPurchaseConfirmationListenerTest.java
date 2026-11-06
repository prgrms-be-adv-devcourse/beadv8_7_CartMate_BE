package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderPurchaseConfirmationListenerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 20, 10, 0);

    private final OrderFacade orderFacade = mock(OrderFacade.class);
    private final OrderPurchaseConfirmationListener listener = new OrderPurchaseConfirmationListener(orderFacade);

    @Test
    @DisplayName("구매확정은 개인 주문 배송(INDIVIDUAL_GROUP)만 기록하고 공동물품 배송은 무시한다")
    void deliveryConfirmed() {
        listener.handle(confirmed(1L, PurchaseType.INDIVIDUAL_GROUP));
        listener.handle(confirmed(2L, PurchaseType.SHARED_GROUP));

        verify(orderFacade).recordPurchaseConfirmed(1L, AT);
        verify(orderFacade, never()).recordPurchaseConfirmed(eq(2L), any());
    }

    private static DeliveryConfirmedEvent confirmed(Long orderId, PurchaseType purchaseType) {
        return new DeliveryConfirmedEvent(100L + orderId, 5L, orderId, purchaseType, AT.minusDays(9), AT, List.of());
    }
}
