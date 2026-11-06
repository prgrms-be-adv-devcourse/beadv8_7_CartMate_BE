package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

public interface IndividualGroupPurchaseOrderClient {

    boolean hasOrders(Long individualGroupPurchaseId, Long productId);
}
