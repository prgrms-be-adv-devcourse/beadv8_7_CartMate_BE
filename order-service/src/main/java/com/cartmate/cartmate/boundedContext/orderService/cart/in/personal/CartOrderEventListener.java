package com.cartmate.cartmate.boundedContext.orderService.cart.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.personal.CartFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 개인 주문이 결제되면 주문한 수량만큼 장바구니에서 뺀다.
// 아웃박스 릴레이가 전달한 것만 받는다(이중 발행 제거, MSA-5). 릴레이가 트랜잭션 밖에서 발행하니 fallbackExecution으로 바로 실행되고,
// 예외는 릴레이로 올라가 다시 시도된다. 수량 차감은 두 번 하면 두 번 빠지므로 eventId로 한 번만 처리한다(CartFacade)
@Component
@RequiredArgsConstructor
public class CartOrderEventListener {

    private final CartFacade cartFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        if (message.event() instanceof OrderPaidEvent event) {
            cartFacade.removeOrderedItems(message.envelope().eventId(), event);
        }
    }
}
