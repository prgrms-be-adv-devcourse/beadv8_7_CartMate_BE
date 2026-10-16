package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class CartSupport {

    private final CartRepository cartRepository;

    Optional<Cart> findWithItems(Long memberId) {
        return cartRepository.findWithItemsByMemberId(memberId);
    }

    Cart getWithItems(Long memberId) {
        return findWithItems(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니가 존재하지 않습니다."));
    }

    Cart save(Cart cart) {
        return cartRepository.save(cart);
    }
}
