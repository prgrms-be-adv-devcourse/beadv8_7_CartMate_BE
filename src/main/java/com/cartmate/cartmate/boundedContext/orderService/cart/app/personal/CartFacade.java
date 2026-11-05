package com.cartmate.cartmate.boundedContext.orderService.cart.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor

public class CartFacade {

    private final CartItemUseCase cartItemUseCase;
    private final CartQueryUseCase cartQueryUseCase;

    // 장바구니 행 락을 잡은 뒤 읽는 항목이 방금 커밋된 다른 담기까지 보도록 문장마다 새 스냅샷을 쓴다
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void addItem(Long memberId, Long inventoryId, int quantity) {
        cartItemUseCase.addItem(memberId, inventoryId, quantity);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long memberId) {
        return cartQueryUseCase.getCart(memberId);
    }

    @Transactional
    public List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds) {
        return cartQueryUseCase.getItemsForOrder(memberId, cartItemIds);
    }

    @Transactional
    public void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        cartItemUseCase.changeQuantity(memberId, cartItemId, quantity);
    }

    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        cartItemUseCase.removeItem(memberId, cartItemId);
    }

    @Transactional
    public void clear(Long memberId) {
        cartItemUseCase.clear(memberId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeOrderedItems(OrderPaidEvent event) {
        cartItemUseCase.removeOrderedItems(event);
    }
}
