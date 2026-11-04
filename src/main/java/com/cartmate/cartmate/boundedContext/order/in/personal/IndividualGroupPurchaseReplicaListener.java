package com.cartmate.cartmate.boundedContext.order.in.personal;

import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.shared.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 아웃박스 릴레이가 트랜잭션 밖에서 넘기는 cart 공동구매 이벤트로 order 쪽 공동구매 사본을 고친다.
// 트랜잭션이 없으니 fallbackExecution으로 바로 실행되고, 예외는 그대로 릴레이로 올라가 다시 시도된다.
// 멱등 · 순서: 사본이 cart의 revision을 비교해 늦게 온 · 중복 이벤트를 버린다
@Component
@RequiredArgsConstructor
public class IndividualGroupPurchaseReplicaListener {

    private final OrderFacade orderFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case IndividualGroupPurchaseOpenedEvent event -> orderFacade.applyIndividualGroupPurchaseOpened(event);
            case IndividualGroupPurchaseUpdatedEvent event -> orderFacade.applyIndividualGroupPurchaseUpdated(event);
            case IndividualGroupPurchaseClosedEvent event -> orderFacade.applyIndividualGroupPurchaseClosed(event);
            default -> {
            }
        }
    }
}
