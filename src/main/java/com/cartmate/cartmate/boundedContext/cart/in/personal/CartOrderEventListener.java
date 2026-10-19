package com.cartmate.cartmate.boundedContext.cart.in.personal;

import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartOrderEventListener {

    private final CartFacade cartFacade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPaidEvent event) {
        try {
            cartFacade.removeOrderedItems(event);
        } catch (Exception e) {
            log.error("결제 완료 장바구니 상품 삭제 실패: orderId={}", event.orderId(), e);
        }
    }
}
