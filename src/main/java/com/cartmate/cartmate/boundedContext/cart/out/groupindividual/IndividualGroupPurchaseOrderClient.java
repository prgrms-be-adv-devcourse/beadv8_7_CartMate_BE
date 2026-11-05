package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

public interface IndividualGroupPurchaseOrderClient {

    boolean hasOrders(Long individualGroupPurchaseId, Long productId);
}
