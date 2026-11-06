package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchaseMember;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseCancelReason;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseCanceledEvent;

import java.util.List;

public final class SharedGroupPurchaseEvents {

    private SharedGroupPurchaseEvents() {
    }

    public static SharedGroupPurchaseCanceledEvent canceled(SharedGroupPurchase purchase,
                                                            SharedGroupPurchaseCancelReason reason) {
        List<Long> participantMemberIds = purchase.getMembers().stream()
                .map(SharedGroupPurchaseMember::getMemberId)
                .toList();
        return new SharedGroupPurchaseCanceledEvent(purchase.getId(), purchase.getCreatorMemberId(),
                participantMemberIds, reason, purchase.getClosedAt());
    }
}
