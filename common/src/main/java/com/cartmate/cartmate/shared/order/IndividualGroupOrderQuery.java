package com.cartmate.cartmate.shared.order;

public interface IndividualGroupOrderQuery {

    boolean hasActiveOrder(Long individualGroupPurchaseId, Long productId);
}
