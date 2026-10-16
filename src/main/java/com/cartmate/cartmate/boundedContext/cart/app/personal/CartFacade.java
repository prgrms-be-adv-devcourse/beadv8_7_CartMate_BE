package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class CartFacade {

    private final CartItemUseCase cartItemUseCase;
    private final CartQueryUseCase cartQueryUseCase;

    @Transactional
    public void addItem(Long memberId, Long inventoryId, int quantity) {
        cartItemUseCase.addItem(memberId, inventoryId, quantity);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long memberId) {
        return cartQueryUseCase.getCart(memberId);
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
}
