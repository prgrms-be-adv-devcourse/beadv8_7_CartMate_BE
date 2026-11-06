package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.shared.order.OrderCancelReason;

import com.cartmate.cartmate.shared.order.OrderType;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IndividualGroupPurchasePaybackListenerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 20, 10, 0);

    private final OrderFacade orderFacade = mock(OrderFacade.class);
    private final IndividualGroupPurchasePaybackListener listener = new IndividualGroupPurchasePaybackListener(orderFacade);

    @Test
    @DisplayName("개인물품 공동구매 주문의 취소와 판매자 항목 취소는 그 주문으로 판정을 일으킨다")
    void cancellationsTriggerJudgment() {
        listener.handle(relayed(new OrderCanceledEvent(1L, 10L, OrderType.INDIVIDUAL_GROUP, OrderCancelReason.BUYER_CANCELED, true, AT)));
        listener.handle(relayed(new OrderSellerItemsCanceledEvent(2L, 10L, 5L, 1_000L, AT)));
        listener.handle(relayed(new OrderCanceledEvent(3L, 10L, OrderType.PERSONAL, OrderCancelReason.BUYER_CANCELED, true, AT)));

        verify(orderFacade).judgeIndividualGroupPurchasePaybackOfOrder(1L);
        verify(orderFacade).judgeIndividualGroupPurchasePaybackOfOrder(2L);
        verify(orderFacade, never()).judgeIndividualGroupPurchasePaybackOfOrder(3L);
    }

    @Test
    @DisplayName("구매확정은 개인 주문 배송(INDIVIDUAL_GROUP)만 기록하고 공동물품 배송은 무시한다")
    void deliveryConfirmed() {
        listener.handle(confirmed(1L, PurchaseType.INDIVIDUAL_GROUP));
        listener.handle(confirmed(2L, PurchaseType.SHARED_GROUP));

        verify(orderFacade).recordPurchaseConfirmed(1L, AT);
        verify(orderFacade, never()).recordPurchaseConfirmed(org.mockito.ArgumentMatchers.eq(2L), any());
    }

    private static OutboxMessageRelayed relayed(DomainEvent event) {
        return new OutboxMessageRelayed(null, event);
    }

    private static DeliveryConfirmedEvent confirmed(Long orderId, PurchaseType purchaseType) {
        return new DeliveryConfirmedEvent(100L + orderId, 5L, orderId, purchaseType, AT.minusDays(9), AT, List.of());
    }
}
