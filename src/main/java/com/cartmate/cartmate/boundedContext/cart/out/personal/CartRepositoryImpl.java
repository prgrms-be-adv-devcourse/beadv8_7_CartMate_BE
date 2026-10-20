package com.cartmate.cartmate.boundedContext.cart.out.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.cart.domain.personal.QCart.cart;
import static com.cartmate.cartmate.boundedContext.cart.domain.personal.QCartItem.cartItem;

public class CartRepositoryImpl implements CartRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public CartRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<Cart> findWithItemsByMemberId(Long memberId) {
        Cart result = queryFactory
                .selectFrom(cart)
                .leftJoin(cart.items, cartItem).fetchJoin()
                .where(cart.memberId.eq(memberId))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Cart> findByMemberIdForUpdate(Long memberId) {
        Cart result = queryFactory
                .selectFrom(cart)
                .where(cart.memberId.eq(memberId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }
}
