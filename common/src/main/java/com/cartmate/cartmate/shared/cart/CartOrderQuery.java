package com.cartmate.cartmate.shared.cart;

import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CartOrderQuery {

    List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds);

    Optional<IndividualGroupPurchaseSnapshot> findIndividualGroupPurchaseSnapshot(Long individualGroupPurchaseId);

    Map<Long, String> findSharedGroupPurchaseTitles(Collection<Long> sharedGroupPurchaseIds);
}
