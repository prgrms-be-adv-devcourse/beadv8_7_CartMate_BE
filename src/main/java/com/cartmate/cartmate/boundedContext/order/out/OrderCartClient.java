package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderCartClient {

    List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds);

    IndividualGroupPurchaseOrderable getIndividualGroupPurchaseOrderable(Long individualGroupPurchaseId,
                                                                        List<Long> productIds, LocalDateTime now);
}
