package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreationFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// order가 낸 공동 주문 생성 결과로 공동구매 단계를 바꾼다: 생성 → ORDER_CREATED, 생성 실패 → FROZEN 복귀.
// 릴레이가 트랜잭션 밖에서 발행하니 fallbackExecution으로 바로 실행되고, 예외는 릴레이로 올라가 다시 시도된다.
// 멱등은 상태 기계로 지킨다: 같은 소식이 다시 와도 이미 바뀐 상태면 그냥 넘긴다
@Component
@RequiredArgsConstructor
public class SharedGroupPurchaseOrderResultListener {

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case SharedGroupOrderCreatedEvent event -> sharedGroupPurchaseFacade.markOrderCreated(event);
            case SharedGroupOrderCreationFailedEvent event -> sharedGroupPurchaseFacade.revertByOrderCreationFailed(event);
            default -> {
            }
        }
    }
}
