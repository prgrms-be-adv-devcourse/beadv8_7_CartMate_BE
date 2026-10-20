package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CartItemUseCase {

    private static final String PERSONAL_ORDER_TYPE = "PERSONAL";

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    void addItem(Long memberId, Long inventoryId, int quantity) {
        CartProductInfo product = getPurchasableProduct(inventoryId);

        Cart cart = cartRepository.findWithItemsByMemberId(memberId)
                .orElseGet(() -> cartRepository.save(Cart.create(memberId)));
        cart.addItem(product.productId(), inventoryId, quantity);
    }

    void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        Cart cart = getCartWithItems(memberId);
        cart.getItem(cartItemId).changeQuantity(quantity);
    }

    void removeItem(Long memberId, Long cartItemId) {
        Cart cart = getCartWithItems(memberId);
        cart.removeItem(cartItemId);
    }

    void clear(Long memberId) {
        cartRepository.findWithItemsByMemberId(memberId)
                .ifPresent(Cart::clear);
    }

    void removeOrderedItems(OrderPaidEvent event) {
        if (!PERSONAL_ORDER_TYPE.equals(event.orderType())) {
            return;
        }
        Map<Long, Integer> orderedQuantities = event.items().stream()
                .collect(Collectors.toMap(
                        OrderPaidEvent.Item::inventoryId,
                        OrderPaidEvent.Item::quantity,
                        Integer::sum
                ));
        cartRepository.findWithItemsByMemberId(event.memberId())
                .ifPresent(cart -> cart.removeOrderedQuantities(orderedQuantities));
    }

    private Cart getCartWithItems(Long memberId) {
        return cartRepository.findWithItemsByMemberId(memberId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_NOT_FOUND));
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
