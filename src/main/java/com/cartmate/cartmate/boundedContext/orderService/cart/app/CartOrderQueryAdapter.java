package com.cartmate.cartmate.boundedContext.orderService.cart.app;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.personal.CartFacade;
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
public class CartOrderQueryAdapter implements CartOrderQuery {

    private final CartFacade cartFacade;
    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;
    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Override
    public List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds) {
        return cartFacade.getCartItemsForOrder(memberId, cartItemIds);
    }

    @Override
    public Optional<IndividualGroupPurchaseSnapshot> findIndividualGroupPurchaseSnapshot(Long individualGroupPurchaseId) {
        return individualGroupPurchaseFacade.findSnapshot(individualGroupPurchaseId);
    }

    @Override
    public Map<Long, String> findSharedGroupPurchaseTitles(Collection<Long> sharedGroupPurchaseIds) {
        return sharedGroupPurchaseFacade.findTitles(sharedGroupPurchaseIds);
    }
}
