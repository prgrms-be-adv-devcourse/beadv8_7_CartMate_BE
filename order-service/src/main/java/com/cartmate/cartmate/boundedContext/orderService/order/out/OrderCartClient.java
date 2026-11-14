package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseSnapshot;

import java.util.List;
import java.util.Optional;

public interface OrderCartClient {

    List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds);

    Optional<IndividualGroupPurchaseSnapshot> findIndividualGroupPurchaseSnapshot(Long individualGroupPurchaseId);
}
