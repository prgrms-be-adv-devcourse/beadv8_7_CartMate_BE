package com.cartmate.cartmate.boundedContext.productService.payback.in;

import com.cartmate.cartmate.boundedContext.productService.payback.app.PaybackFacade;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackOrderItem;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPaybackListener {

    private final PaybackFacade paybackFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(OrderCreatedEvent event) {
        if (isIndividualGroup(event.orderType(), event.individualGroupPurchaseId())) {
            paybackFacade.applyOrderPlaced(event.orderId(), event.individualGroupPurchaseId(), event.memberId(),
                    event.items().stream()
                            .map(item -> PaybackOrderItem.of(item.orderItemId(), item.productId(), item.sellerId(),
                                    item.quantity()))
                            .toList(),
                    OrderStatus.PAYMENT_PENDING);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(OrderPaidEvent event) {
        if (isIndividualGroup(event.orderType(), event.individualGroupPurchaseId())) {
            paybackFacade.applyOrderPlaced(event.orderId(), event.individualGroupPurchaseId(), event.memberId(),
                    event.items().stream()
                            .map(item -> PaybackOrderItem.of(item.orderItemId(), item.productId(), item.sellerId(),
                                    item.quantity()))
                            .toList(),
                    OrderStatus.PAID);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(OrderCanceledEvent event) {
        if (event.orderType() == OrderType.INDIVIDUAL_GROUP) {
            paybackFacade.applyOrderCanceled(event.orderId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(OrderSellerItemsCanceledEvent event) {
        paybackFacade.applyOrderSellerItemsCanceled(event.orderId(), event.sellerId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(DeliveryConfirmedEvent event) {
        if (event.purchaseType() == PurchaseType.INDIVIDUAL_GROUP) {
            paybackFacade.applyPurchaseConfirmed(event.orderId(), event.sellerId());
        }
    }

    private static boolean isIndividualGroup(OrderType orderType, Long individualGroupPurchaseId) {
        return orderType == OrderType.INDIVIDUAL_GROUP && individualGroupPurchaseId != null;
    }
}
