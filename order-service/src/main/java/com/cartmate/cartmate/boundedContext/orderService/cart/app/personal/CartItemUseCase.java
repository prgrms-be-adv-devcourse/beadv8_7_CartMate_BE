package com.cartmate.cartmate.boundedContext.orderService.cart.app.personal;

import com.cartmate.cartmate.shared.order.OrderType;

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

    // 같은 회원의 담기를 장바구니 행 락으로 줄 세운다. 락 없이 읽고 쓰면 동시에 같은 상품을 담을 때 항목이 둘 insert돼
    // uk_cart_item_cart_inventory 위반이 난다. 장바구니가 없으면 같은 트랜잭션에서 "없을 때만 insert"로 만든다
    // (저장하다 unique 위반이 나면 영속성 컨텍스트가 망가지고, 별도 트랜잭션은 커넥션을 하나 더 잡아 풀이 마를 수 있다).
    // 항목은 락을 잡은 뒤 지연 로딩으로 따로 읽는다. 락과 같은 문장에서 조인해 읽으면 MySQL이 항목을 이전 스냅샷으로 읽어
    // 방금 커밋된 항목을 놓친다. 그래서 CartFacade.addItem은 READ_COMMITTED다 (문장마다 새 스냅샷)
    private Cart lockCart(Long memberId) {
        if (!cartRepository.existsByMemberId(memberId)) {
            cartRepository.insertIfAbsent(memberId, LocalDateTime.now(clock));
        }
        return cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_NOT_FOUND));
    }

    // 아래 항목 변경(수량 변경 · 삭제 · 비우기 · 결제 후 차감)도 담기와 같은 장바구니 행 락으로 줄 세운다(ARCH-10#1).
    // 락 없이 읽으면 담기가 수량을 올리려는 항목을 다른 쪽이 먼저 지워 UPDATE 0건 → StaleStateException(500)이 나거나,
    // 서로 다른 스냅샷으로 같은 항목을 고쳐 한쪽 변경이 사라진다. 항목은 락 뒤에 따로 읽으므로 Facade는 READ_COMMITTED다
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
        if (event.orderType() != OrderType.PERSONAL) {
            return;
        }
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
