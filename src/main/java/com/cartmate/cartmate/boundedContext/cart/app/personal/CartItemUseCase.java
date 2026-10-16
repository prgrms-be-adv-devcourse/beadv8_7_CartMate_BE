package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class CartItemUseCase {

    private final CartSupport cartSupport;
    private final ProductClient productClient;

    void addItem(Long memberId, Long inventoryId, int quantity) {
        CartProductInfo product = getPurchasableProduct(inventoryId);

        Cart cart = cartSupport.findWithItems(memberId)
                .orElseGet(() -> cartSupport.save(Cart.create(memberId)));
        cart.addItem(product.productId(), inventoryId, quantity);
    }

    void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        Cart cart = cartSupport.getWithItems(memberId);
        cart.getItem(cartItemId).changeQuantity(quantity);
    }

    void removeItem(Long memberId, Long cartItemId) {
        Cart cart = cartSupport.getWithItems(memberId);
        cart.removeItem(cartItemId);
    }

    void clear(Long memberId) {
        cartSupport.findWithItems(memberId)
                .ifPresent(Cart::clear);
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
