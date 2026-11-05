package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.boundedContext.orderService.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.dto.IndividualGroupPurchaseSnapshot;

import java.util.List;
import java.util.Optional;

public interface OrderCartClient {

    List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds);

    // 공동구매 사본이 아직 없을 때만 쓴다(잠그지 않음)
    Optional<IndividualGroupPurchaseSnapshot> findIndividualGroupPurchaseSnapshot(Long individualGroupPurchaseId);
}
