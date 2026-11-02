package com.cartmate.cartmate.boundedContext.cart.app;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import com.cartmate.cartmate.shared.cart.CartOrderQuery;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    public IndividualGroupPurchaseOrderable getIndividualGroupPurchaseOrderable(Long individualGroupPurchaseId,
                                                                               List<Long> productIds, LocalDateTime now) {
        return individualGroupPurchaseFacade.getOrderable(individualGroupPurchaseId, productIds, now);
    }

    @Override
    public Map<Long, String> findSharedGroupPurchaseTitles(Collection<Long> sharedGroupPurchaseIds) {
        return sharedGroupPurchaseFacade.findTitles(sharedGroupPurchaseIds);
    }
}
