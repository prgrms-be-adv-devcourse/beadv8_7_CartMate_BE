package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchaseItem;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseItemQuantityResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.ProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseItemUseCase {

    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final ProductClient productClient;
    private final Clock clock;

    void addItem(Long memberId, Long sharedGroupPurchaseId, Long inventoryId, int quantity) {
        CartProductInfo product = getPurchasableProduct(inventoryId);
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId);
        purchase.addItem(memberId, product.productId(), inventoryId, quantity, LocalDateTime.now(clock));
    }

    void changeItemQuantity(Long memberId, Long sharedGroupPurchaseId, Long itemId, int quantity) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .changeItemQuantity(memberId, itemId, quantity, LocalDateTime.now(clock));
    }

    SharedGroupPurchaseItemQuantityResponse adjustItemQuantity(Long memberId, Long sharedGroupPurchaseId, Long itemId, int delta) {
        SharedGroupPurchaseItem item = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .adjustItemQuantity(memberId, itemId, delta, LocalDateTime.now(clock));
        return SharedGroupPurchaseItemQuantityResponse.from(item);
    }

    void changeItemOption(Long memberId, Long sharedGroupPurchaseId, Long itemId, Long inventoryId) {
        CartProductInfo product = getPurchasableProduct(inventoryId);
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId);
        purchase.changeItemOption(memberId, itemId, product.productId(), inventoryId, LocalDateTime.now(clock));
    }

    void removeItem(Long memberId, Long sharedGroupPurchaseId, Long itemId) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .removeItem(memberId, itemId, LocalDateTime.now(clock));
    }

    private CartProductInfo getPurchasableProduct(Long inventoryId) {
        CartProductInfo product = productClient.findByInventoryId(inventoryId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_PRODUCT_OPTION_NOT_FOUND));
        if (!product.purchasable()) {
            throw new ServiceException(CartErrorCode.CART_PRODUCT_NOT_PURCHASABLE);
        }
        return product;
    }
}
