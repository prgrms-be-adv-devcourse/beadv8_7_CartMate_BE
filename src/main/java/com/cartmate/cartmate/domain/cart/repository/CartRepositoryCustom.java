package com.cartmate.cartmate.domain.cart.repository;

import com.cartmate.cartmate.domain.cart.entity.Cart;

import java.util.Optional;

public interface CartRepositoryCustom {

    Optional<Cart> findWithItemsByMemberId(Long memberId);
}
