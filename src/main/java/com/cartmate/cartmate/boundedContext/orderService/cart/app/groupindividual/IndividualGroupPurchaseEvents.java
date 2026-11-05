package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.dto.IndividualGroupPurchaseSnapshot;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;

import java.time.LocalDateTime;

// 공동구매 엔티티 → order에 알리는 이벤트 · 스냅샷. 모든 발행 지점이 같은 모양을 쓰게 한곳에 모은다
public final class IndividualGroupPurchaseEvents {

    private IndividualGroupPurchaseEvents() {
    }

    public static IndividualGroupPurchaseOpenedEvent opened(IndividualGroupPurchase purchase, LocalDateTime now) {
        return new IndividualGroupPurchaseOpenedEvent(purchase.getId(), purchase.getDeadlineAt(), purchase.productIds(),
                purchase.getRevision(), now);
    }

    public static IndividualGroupPurchaseUpdatedEvent updated(IndividualGroupPurchase purchase, LocalDateTime now) {
        return new IndividualGroupPurchaseUpdatedEvent(purchase.getId(), purchase.getDeadlineAt(), purchase.productIds(),
                purchase.getRevision(), now);
    }

    public static IndividualGroupPurchaseClosedEvent closed(IndividualGroupPurchase purchase,
                                                            IndividualGroupPurchaseCloseReason reason) {
        return new IndividualGroupPurchaseClosedEvent(purchase.getId(), purchase.getOrigin().name(), reason.name(),
                purchase.getDeadlineAt(), purchase.getClosedAt(), purchase.getRevision());
    }

    static IndividualGroupPurchaseSnapshot snapshot(IndividualGroupPurchase purchase) {
        return new IndividualGroupPurchaseSnapshot(purchase.getId(), purchase.isClosed(), purchase.getDeadlineAt(),
                purchase.getClosedAt(), purchase.productIds(), purchase.getRevision());
    }
}
