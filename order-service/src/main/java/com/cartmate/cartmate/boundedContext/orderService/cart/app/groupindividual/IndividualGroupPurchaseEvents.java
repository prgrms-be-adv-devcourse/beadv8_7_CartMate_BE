package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseSnapshot;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;

import java.time.LocalDateTime;

public final class IndividualGroupPurchaseEvents {

    private IndividualGroupPurchaseEvents() {
    }

    public static IndividualGroupPurchaseOpenedEvent opened(IndividualGroupPurchase purchase, LocalDateTime now) {
        return new IndividualGroupPurchaseOpenedEvent(purchase.getId(), purchase.getDeadlineAt(), purchase.productIds(),
                purchase.getRevision(), now, purchase.getPaybackMinBuyers(), purchase.getPaybackAmount());
    }

    public static IndividualGroupPurchaseUpdatedEvent updated(IndividualGroupPurchase purchase, LocalDateTime now) {
        return new IndividualGroupPurchaseUpdatedEvent(purchase.getId(), purchase.getDeadlineAt(), purchase.productIds(),
                purchase.getRevision(), now);
    }

    public static IndividualGroupPurchaseClosedEvent closed(IndividualGroupPurchase purchase,
                                                            IndividualGroupPurchaseCloseReason reason) {
        return new IndividualGroupPurchaseClosedEvent(purchase.getId(), purchase.getOrigin(), reason,
                purchase.getDeadlineAt(), purchase.getClosedAt(), purchase.getRevision(),
                purchase.getPaybackMinBuyers(), purchase.getPaybackAmount());
    }

    static IndividualGroupPurchaseSnapshot snapshot(IndividualGroupPurchase purchase) {
        return new IndividualGroupPurchaseSnapshot(purchase.getId(), purchase.isClosed(), purchase.getDeadlineAt(),
                purchase.getClosedAt(), purchase.productIds(), purchase.getRevision());
    }
}
