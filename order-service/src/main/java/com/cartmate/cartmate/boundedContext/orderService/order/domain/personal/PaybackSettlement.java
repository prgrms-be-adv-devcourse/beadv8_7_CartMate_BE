package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.shared.order.OrderStatus;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class PaybackSettlement {

    private PaybackSettlement() {
    }

    public static Optional<Integer> confirmedBuyerCount(Collection<Order> orders, Set<Long> confirmedOrderIds) {
        Set<Long> confirmedBuyers = new HashSet<>();
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.CANCELED) {
                continue;
            }
            if (order.getStatus() != OrderStatus.PAID || !confirmedOrderIds.contains(order.getId())) {
                return Optional.empty();
            }
            confirmedBuyers.add(order.getBuyerMemberId());
        }
        return Optional.of(confirmedBuyers.size());
    }
}
