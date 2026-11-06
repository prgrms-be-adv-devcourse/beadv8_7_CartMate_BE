package com.cartmate.cartmate.boundedContext.orderService.cart.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.personal.CartRepository;
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
        // 잠그지 않는다. order 주문 생성 트랜잭션이 shared 포트로 부르므로, 잠그면 order가 cart 스키마 행을 잠그게 된다
        // (모듈을 넘는 락 금지). 같은 회원 · 같은 상품의 중복 결제 대기는 order 쪽 unique(order_item.pending_buyer_member_id)가 막는다
        Cart cart = cartRepository.findWithItemsByMemberId(memberId)
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
