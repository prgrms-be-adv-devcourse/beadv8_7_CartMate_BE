package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

import java.util.List;

public interface IndividualGroupPurchaseOrderReplicaRepositoryCustom {

    // 결제 완료 주문의 항목 중 판매 중지로 취소되지 않은 것만, 주문 · 항목 순서대로
    List<IndividualGroupPurchaseOrderLine> findPaidLines(Long individualGroupPurchaseId);
}
