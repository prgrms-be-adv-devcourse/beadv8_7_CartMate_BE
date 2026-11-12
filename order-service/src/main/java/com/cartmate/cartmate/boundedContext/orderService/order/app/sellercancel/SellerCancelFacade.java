package com.cartmate.cartmate.boundedContext.orderService.order.app.sellercancel;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliverySellerCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 사유 취소: 배송 서버가 판매자 배송을 이미 취소했으므로, 거절하지 않고 그 판매자 항목을 취소하고 환불 이벤트를 낸다.
 * 결제 완료가 아니거나 취소할 항목이 없으면(같은 이벤트 재수신, 판매 중지와 겹침) 아무것도 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SellerCancelFacade {

    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @Transactional
    public void apply(DeliverySellerCanceledEvent event) {
        if (event.purchaseType() == PurchaseType.SHARED_GROUP) {
            sharedGroupOrderFacade.cancelSellerItems(event.orderId(), event.sellerId(), event.cancelReason());
            return;
        }
        orderFacade.cancelSellerItems(event.orderId(), event.sellerId(), event.cancelReason());
    }
}
