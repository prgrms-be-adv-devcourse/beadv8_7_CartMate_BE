package com.cartmate.cartmate.domain.cart.groupindividual.client;

import java.util.List;


public interface IndividualGroupPurchaseOrderClient {
    boolean hasOrders(Long individualGroupPurchaseId, Long productId);
    List<IndividualGroupPurchaseOrderLine> findOrderLines(Long individualGroupPurchaseId);
}
