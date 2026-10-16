package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseItemUseCase {

    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final ProductClient productClient;

    void addItem(Long memberId, Long sharedGroupPurchaseId, Long inventoryId, int quantity) {
        CartProductInfo product = getPurchasableProduct(inventoryId);
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId);
        purchase.addItem(memberId, product.productId(), inventoryId, quantity, LocalDateTime.now());
    }

    void changeItemQuantity(Long memberId, Long sharedGroupPurchaseId, Long itemId, int quantity) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .changeItemQuantity(memberId, itemId, quantity, LocalDateTime.now());
    }

    void changeItemOption(Long memberId, Long sharedGroupPurchaseId, Long itemId, Long inventoryId) {
        CartProductInfo product = getPurchasableProduct(inventoryId);
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId);
        purchase.changeItemOption(memberId, itemId, product.productId(), inventoryId, LocalDateTime.now());
    }

    void removeItem(Long memberId, Long sharedGroupPurchaseId, Long itemId) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .removeItem(memberId, itemId, LocalDateTime.now());
    }

    private CartProductInfo getPurchasableProduct(Long inventoryId) {
        CartProductInfo product = productClient.findByInventoryId(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품 옵션입니다."));
        if (!product.purchasable()) {
            throw new IllegalArgumentException("구매할 수 없는 상품입니다.");
        }
        return product;
    }
}
