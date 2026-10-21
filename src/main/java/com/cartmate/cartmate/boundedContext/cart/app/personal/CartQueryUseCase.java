package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
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

    List<CartOrderItem> getItemsForOrder(Long memberId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new ServiceException(CartErrorCode.CART_ORDER_ITEMS_REQUIRED);
        }
        Cart cart = cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_NOT_FOUND));
        return cartItemIds.stream()
                .distinct()
                .map(cart::getItem)
                .map(item -> new CartOrderItem(item.getId(), item.getProductId(), item.getInventoryId(), item.getQuantity()))
                .toList();
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
