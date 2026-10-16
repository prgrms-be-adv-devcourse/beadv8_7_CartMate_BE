package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.cart.domain.Cart;

import java.util.Optional;

public interface CartRepositoryCustom {

    Optional<Cart> findWithItemsByMemberId(Long memberId);
}
