package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

import java.util.List;


public interface IndividualGroupPurchaseOrderClient {
    boolean hasOrders(Long individualGroupPurchaseId, Long productId);
    List<IndividualGroupPurchaseOrderLine> findOrderLines(Long individualGroupPurchaseId);
}
