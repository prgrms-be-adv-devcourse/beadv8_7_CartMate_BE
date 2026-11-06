package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.shared.order.IndividualGroupOrderQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 상품을 빼도 되는지는 읽기 모델(약 1초 늦음)이 아니라 order에 바로 묻는다. 방금 생긴 결제 대기 주문을 놓치지 않으려고
@Component
@RequiredArgsConstructor
class OrderQueryIndividualGroupPurchaseOrderClient implements IndividualGroupPurchaseOrderClient {

    private final IndividualGroupOrderQuery individualGroupOrderQuery;

    @Override
    public boolean hasOrders(Long individualGroupPurchaseId, Long productId) {
        return individualGroupOrderQuery.hasActiveOrder(individualGroupPurchaseId, productId);
    }
}
