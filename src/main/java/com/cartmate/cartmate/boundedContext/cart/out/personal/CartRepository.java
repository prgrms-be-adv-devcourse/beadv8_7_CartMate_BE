package com.cartmate.cartmate.boundedContext.cart.out.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long>, CartRepositoryCustom {

    Optional<Cart> findByMemberId(Long memberId);
}
