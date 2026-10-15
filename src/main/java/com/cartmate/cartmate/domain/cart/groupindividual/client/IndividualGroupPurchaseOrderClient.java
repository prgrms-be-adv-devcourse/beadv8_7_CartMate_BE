package com.cartmate.cartmate.domain.cart.groupindividual.client;


public interface IndividualGroupPurchaseOrderClient {

    boolean hasOrders(Long individualGroupPurchaseId, Long productId);
}
