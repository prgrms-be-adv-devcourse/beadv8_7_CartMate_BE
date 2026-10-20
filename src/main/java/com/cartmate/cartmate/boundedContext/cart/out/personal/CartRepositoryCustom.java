package com.cartmate.cartmate.boundedContext.cart.out.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;

import java.util.Optional;

public interface CartRepositoryCustom {

    Optional<Cart> findWithItemsByMemberId(Long memberId);

    Optional<Cart> findByMemberIdForUpdate(Long memberId);
}
