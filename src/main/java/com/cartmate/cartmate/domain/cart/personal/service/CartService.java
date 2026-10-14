package com.cartmate.cartmate.domain.cart.personal.service;

import com.cartmate.cartmate.domain.cart.client.CartProductInfo;
import com.cartmate.cartmate.domain.cart.client.ProductClient;
import com.cartmate.cartmate.domain.cart.personal.dto.CartItemResponse;
import com.cartmate.cartmate.domain.cart.personal.dto.CartResponse;
import com.cartmate.cartmate.domain.cart.personal.entity.Cart;
import com.cartmate.cartmate.domain.cart.personal.entity.CartItem;
import com.cartmate.cartmate.domain.cart.personal.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    @Transactional
    public void addItem(Long memberId, Long inventoryId, int quantity) {
        CartProductInfo product = productClient.findByInventoryId(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품 옵션입니다."));
        if (!product.purchasable()) {
            throw new IllegalArgumentException("구매할 수 없는 상품입니다.");
        }

        Cart cart = cartRepository.findWithItemsByMemberId(memberId)
                .orElseGet(() -> cartRepository.save(Cart.create(memberId)));
        cart.addItem(product.productId(), inventoryId, quantity);
    }

    public CartResponse getCart(Long memberId) {
        return cartRepository.findWithItemsByMemberId(memberId)
                .map(this::toResponse)
                .orElseGet(CartResponse::empty);
    }

    @Transactional
    public void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        Cart cart = getCartWithItems(memberId);
        cart.getItem(cartItemId).changeQuantity(quantity);
    }

    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        Cart cart = getCartWithItems(memberId);
        cart.removeItem(cartItemId);
    }

    @Transactional
    public void clear(Long memberId) {
        cartRepository.findWithItemsByMemberId(memberId)
                .ifPresent(Cart::clear);
    }

    private Cart getCartWithItems(Long memberId) {
        return cartRepository.findWithItemsByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니가 존재하지 않습니다."));
    }

    private CartResponse toResponse(Cart cart) {
        List<Long> inventoryIds = cart.getItems().stream()
                .map(CartItem::getInventoryId)
                .toList();
        Map<Long, CartProductInfo> products = productClient.findAllByInventoryIds(inventoryIds).stream()
                .collect(Collectors.toMap(CartProductInfo::inventoryId, Function.identity()));

        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> CartItemResponse.of(item, products.get(item.getInventoryId())))
                .toList();
        return CartResponse.of(cart.getId(), items);
    }
}
