package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PaybackSettlement {

    private PaybackSettlement() {
    }

    public static Optional<Integer> confirmedBuyerCount(Collection<Order> orders,
                                                        Map<Long, PaybackOrderOutcomeType> outcomes) {
        Set<Long> confirmedBuyers = new HashSet<>();
        for (Order order : orders) {
            PaybackOrderOutcomeType outcome = outcomes.get(order.orderId());
            if (order.status() == IndividualGroupPurchaseOrderReplicaStatus.CANCELED
                    || outcome == PaybackOrderOutcomeType.CANCELED) {
                continue;
            }
            if (outcome != PaybackOrderOutcomeType.CONFIRMED) {
                return Optional.empty();
            }
            confirmedBuyers.add(order.buyerMemberId());
        }
        return Optional.of(confirmedBuyers.size());
    }

    public record Order(Long orderId, Long buyerMemberId, IndividualGroupPurchaseOrderReplicaStatus status) {
    }
}
