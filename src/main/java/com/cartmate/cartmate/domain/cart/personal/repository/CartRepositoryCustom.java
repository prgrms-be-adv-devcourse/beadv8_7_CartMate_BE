package com.cartmate.cartmate.domain.cart.personal.repository;

import com.cartmate.cartmate.domain.cart.personal.entity.Cart;

import java.util.Optional;

public interface CartRepositoryCustom {

    Optional<Cart> findWithItemsByMemberId(Long memberId);
}
