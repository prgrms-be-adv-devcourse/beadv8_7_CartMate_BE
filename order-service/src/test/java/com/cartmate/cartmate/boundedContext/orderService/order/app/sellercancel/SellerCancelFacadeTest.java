package com.cartmate.cartmate.boundedContext.orderService.order.app.sellercancel;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliverySellerCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SellerCancelFacadeTest {

    private static final long ORDER_ID = 100L;
    private static final long SELLER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    private final OrderFacade orderFacade = mock(OrderFacade.class);
    private final SharedGroupOrderFacade sharedGroupOrderFacade = mock(SharedGroupOrderFacade.class);
    private final SellerCancelFacade facade = new SellerCancelFacade(orderFacade, sharedGroupOrderFacade);

    @Test
    @DisplayName("개인 주문의 판매자 사유 취소를 받으면 그 판매자 항목을 판매자가 고른 사유로 취소한다")
    void appliesToPersonalOrder() {
        facade.apply(new DeliverySellerCanceledEvent(PurchaseType.INDIVIDUAL_GROUP, ORDER_ID, SELLER_ID,
                DeliveryCancelReason.SELLER_OUT_OF_STOCK, NOW));

        verify(orderFacade).cancelSellerItems(ORDER_ID, SELLER_ID, DeliveryCancelReason.SELLER_OUT_OF_STOCK);
        verifyNoInteractions(sharedGroupOrderFacade);
    }

    @Test
    @DisplayName("공동구매의 판매자 사유 취소를 받으면 공동 주문의 그 판매자 항목을 판매자가 고른 사유로 취소한다")
    void appliesToSharedGroupOrder() {
        facade.apply(new DeliverySellerCanceledEvent(PurchaseType.SHARED_GROUP, ORDER_ID, SELLER_ID,
                DeliveryCancelReason.SELLER_PRODUCT_ISSUE, NOW));

        verify(sharedGroupOrderFacade).cancelSellerItems(ORDER_ID, SELLER_ID, DeliveryCancelReason.SELLER_PRODUCT_ISSUE);
        verifyNoInteractions(orderFacade);
    }
}
