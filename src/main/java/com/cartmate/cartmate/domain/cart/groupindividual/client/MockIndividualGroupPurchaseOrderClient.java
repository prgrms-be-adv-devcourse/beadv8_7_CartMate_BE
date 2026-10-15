package com.cartmate.cartmate.domain.cart.groupindividual.client;

import org.springframework.stereotype.Component;


@Component
public class MockIndividualGroupPurchaseOrderClient implements IndividualGroupPurchaseOrderClient {

    @Override
    public boolean hasOrders(Long individualGroupPurchaseId, Long productId) {
        return false;
    }
}
