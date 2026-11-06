package com.cartmate.cartmate.boundedContext.orderService.cart.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor

public class CartFacade {

    private static final String ORDER_PAID_CONSUMER = "cart.order-paid";

    private final CartItemUseCase cartItemUseCase;
    private final CartQueryUseCase cartQueryUseCase;
    private final ProcessedEvents processedEvents;

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

    // 결제된 개인 주문의 수량을 장바구니에서 뺀다. 릴레이는 같은 이벤트를 다시 보낼 수 있고 차감은 두 번 하면 두 번 빠지므로,
    // 같은 트랜잭션에서 eventId를 먼저 기록해 처음 온 것만 처리한다
    @Transactional
    public void removeOrderedItems(String eventId, OrderPaidEvent event) {
        if (!processedEvents.markFirst(ORDER_PAID_CONSUMER, eventId)) {
            return;
        }
        cartItemUseCase.removeOrderedItems(event);
    }
}
