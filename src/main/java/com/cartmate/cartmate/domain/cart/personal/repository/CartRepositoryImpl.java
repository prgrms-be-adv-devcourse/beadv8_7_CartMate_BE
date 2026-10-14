package com.cartmate.cartmate.domain.cart.personal.repository;

import com.cartmate.cartmate.domain.cart.personal.entity.Cart;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static com.cartmate.cartmate.domain.cart.personal.entity.QCart.cart;
import static com.cartmate.cartmate.domain.cart.personal.entity.QCartItem.cartItem;

@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Cart> findWithItemsByMemberId(Long memberId) {
        Cart result = queryFactory
                .selectFrom(cart)
                .leftJoin(cart.items, cartItem).fetchJoin()
                .where(cart.memberId.eq(memberId))
                .fetchOne();
        return Optional.ofNullable(result);
    }
}
