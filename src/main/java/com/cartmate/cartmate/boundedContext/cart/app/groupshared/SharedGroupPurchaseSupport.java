package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseItem;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseSupport {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final ProductClient productClient;

    SharedGroupPurchase getPurchaseForUpdate(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseRepository.findByIdForUpdate(sharedGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
    }

    Set<Long> findUnavailableInventoryIds(SharedGroupPurchase purchase) {
        return productClient.findUnavailableInventoryIds(inventoryIdsOf(purchase));
    }

    List<Long> inventoryIdsOf(SharedGroupPurchase purchase) {
        return purchase.getItems().stream()
                .map(SharedGroupPurchaseItem::getInventoryId)
                .toList();
    }
}
