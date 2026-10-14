package com.cartmate.cartmate.domain.cart.personal.repository;

import com.cartmate.cartmate.domain.cart.personal.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long>, CartRepositoryCustom {

    Optional<Cart> findByMemberId(Long memberId);
}
