package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.shared.order.IndividualGroupOrderQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderQueryIndividualGroupPurchaseOrderClient implements IndividualGroupPurchaseOrderClient {

    private final IndividualGroupOrderQuery individualGroupOrderQuery;

    @Override
    public boolean hasOrders(Long individualGroupPurchaseId, Long productId) {
        return individualGroupOrderQuery.hasActiveOrder(individualGroupPurchaseId, productId);
    }
}
