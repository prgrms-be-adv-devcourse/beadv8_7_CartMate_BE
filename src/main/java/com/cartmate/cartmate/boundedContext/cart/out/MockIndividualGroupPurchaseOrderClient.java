package com.cartmate.cartmate.boundedContext.cart.out;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockIndividualGroupPurchaseOrderClient implements IndividualGroupPurchaseOrderClient {

    @Override
    public boolean hasOrders(Long individualGroupPurchaseId, Long productId) {
        return false;
    }

    @Override
    public List<IndividualGroupPurchaseOrderLine> findOrderLines(Long individualGroupPurchaseId) {
        return List.of();
    }
}
