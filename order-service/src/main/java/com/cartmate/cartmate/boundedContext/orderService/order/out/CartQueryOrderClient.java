package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.cart.CartOrderQuery;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class CartQueryOrderClient implements OrderCartClient, OrderSharedGroupPurchaseClient {

    private final CartOrderQuery cartOrderQuery;

    @Override
    public List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds) {
        return cartOrderQuery.getCartItemsForOrder(memberId, cartItemIds);
    }

    @Override
    public Optional<IndividualGroupPurchaseSnapshot> findIndividualGroupPurchaseSnapshot(Long individualGroupPurchaseId) {
        return cartOrderQuery.findIndividualGroupPurchaseSnapshot(individualGroupPurchaseId);
    }

    @Override
    public Map<Long, String> findTitles(Collection<Long> sharedGroupPurchaseIds) {
        return cartOrderQuery.findSharedGroupPurchaseTitles(sharedGroupPurchaseIds);
    }
}
