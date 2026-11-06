package com.cartmate.cartmate.boundedContext.orderService.cart.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.personal.CartRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CartItemUseCase {

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final Clock clock;

    void addItem(Long memberId, Long inventoryId, int quantity) {
        CartProductInfo product = getPurchasableProduct(inventoryId);

        Cart cart = lockCart(memberId);
        cart.addItem(product.productId(), inventoryId, quantity);
    }

    private Cart lockCart(Long memberId) {
        if (!cartRepository.existsByMemberId(memberId)) {
            cartRepository.insertIfAbsent(memberId, LocalDateTime.now(clock));
        }
        return cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_NOT_FOUND));
    }

    void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        Cart cart = getLockedCart(memberId);
        cart.getItem(cartItemId).changeQuantity(quantity);
    }

    void removeItem(Long memberId, Long cartItemId) {
        Cart cart = getLockedCart(memberId);
        cart.removeItem(cartItemId);
    }

    void clear(Long memberId) {
        cartRepository.findByMemberIdForUpdate(memberId)
                .ifPresent(Cart::clear);
    }

    void removeOrderedItems(OrderPaidEvent event) {
        Map<Long, Integer> orderedQuantities = event.items().stream()
                .collect(Collectors.toMap(
                        OrderPaidEvent.Item::inventoryId,
                        OrderPaidEvent.Item::quantity,
                        Integer::sum
                ));
        cartRepository.findByMemberIdForUpdate(event.memberId())
                .ifPresent(cart -> cart.removeOrderedQuantities(orderedQuantities));
    }

    private Cart getLockedCart(Long memberId) {
        return cartRepository.findByMemberIdForUpdate(memberId)
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
