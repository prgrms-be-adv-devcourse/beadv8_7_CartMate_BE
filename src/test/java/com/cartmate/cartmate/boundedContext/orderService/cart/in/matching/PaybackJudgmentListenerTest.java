package com.cartmate.cartmate.boundedContext.orderService.cart.in.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.matching.CartMatchingFacade;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.etcService.shared.delivery.event.DeliveryConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaybackJudgmentListenerTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 11, 20, 10, 0);

    private final CartMatchingFacade facade = mock(CartMatchingFacade.class);
    private final PaybackJudgmentListener listener = new PaybackJudgmentListener(facade);

    @Test
    @DisplayName("개인 주문 · 개인물품 주문의 구매확정만 기록한다 (delivery PurchaseType 이름이 바뀌면 이 테스트가 깨진다)")
    void individualGroupOnly() {
        listener.handle(confirmed(1L, PurchaseType.INDIVIDUAL_GROUP));
        listener.handle(confirmed(2L, PurchaseType.SHARED_GROUP));

        verify(facade).recordPaybackOrderConfirmed(1L, CONFIRMED_AT);
        verify(facade, never()).recordPaybackOrderConfirmed(org.mockito.ArgumentMatchers.eq(2L), any());
    }

    private static DeliveryConfirmedEvent confirmed(Long orderId, PurchaseType purchaseType) {
        return new DeliveryConfirmedEvent(100L + orderId, 1L, orderId, purchaseType,
                CONFIRMED_AT.minusDays(9), CONFIRMED_AT, List.of());
    }
}
