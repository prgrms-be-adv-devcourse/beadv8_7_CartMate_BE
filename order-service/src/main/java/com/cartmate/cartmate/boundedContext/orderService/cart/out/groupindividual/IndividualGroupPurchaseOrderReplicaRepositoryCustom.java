package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import java.util.List;

public interface IndividualGroupPurchaseOrderReplicaRepositoryCustom {

    List<IndividualGroupPurchaseOrderLine> findPaidLines(Long individualGroupPurchaseId);
}
