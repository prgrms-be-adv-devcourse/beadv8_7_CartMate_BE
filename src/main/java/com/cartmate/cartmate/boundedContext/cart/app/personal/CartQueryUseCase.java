package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CartQueryUseCase {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    CartResponse getCart(Long memberId) {
        return cartRepository.findWithItemsByMemberId(memberId)
                .map(this::toResponse)
                .orElseGet(CartResponse::empty);
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
