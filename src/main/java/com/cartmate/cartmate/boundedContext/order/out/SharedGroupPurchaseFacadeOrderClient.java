package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
@RequiredArgsConstructor
class SharedGroupPurchaseFacadeOrderClient implements OrderSharedGroupPurchaseClient {

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Override
    public Map<Long, String> findTitles(Collection<Long> sharedGroupPurchaseIds) {
        return sharedGroupPurchaseFacade.findTitles(sharedGroupPurchaseIds);
    }
}
