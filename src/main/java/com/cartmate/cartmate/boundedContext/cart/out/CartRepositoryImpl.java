package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.cart.domain.Cart;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.cart.domain.QCart.cart;
import static com.cartmate.cartmate.boundedContext.cart.domain.QCartItem.cartItem;

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
