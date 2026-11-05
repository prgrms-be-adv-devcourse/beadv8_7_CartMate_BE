package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.boundedContext.orderService.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.order.event.OrderCreatedEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 아웃박스 릴레이가 트랜잭션 밖에서 넘기는 order 이벤트로 개인물품 공동구매 주문 읽기 모델을 고친다.
// 트랜잭션이 없으니 fallbackExecution으로 바로 실행되고, 예외는 그대로 릴레이로 올라가 다시 시도된다
@Component
@RequiredArgsConstructor
public class IndividualGroupPurchaseOrderReplicaListener {

    private static final String INDIVIDUAL_GROUP = "INDIVIDUAL_GROUP";

    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case OrderCreatedEvent event when INDIVIDUAL_GROUP.equals(event.orderType()) ->
                    individualGroupPurchaseFacade.applyOrderCreated(event);
            case OrderPaidEvent event when INDIVIDUAL_GROUP.equals(event.orderType()) ->
                    individualGroupPurchaseFacade.applyOrderPaid(event);
            case OrderCanceledEvent event when INDIVIDUAL_GROUP.equals(event.orderType()) ->
                    individualGroupPurchaseFacade.applyOrderCanceled(event);
            case OrderSellerItemsCanceledEvent event -> individualGroupPurchaseFacade.applyOrderSellerItemsCanceled(event);
            default -> {
            }
        }
    }
}
