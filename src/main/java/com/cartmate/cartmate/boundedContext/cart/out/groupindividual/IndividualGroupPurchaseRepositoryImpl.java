package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.QIndividualGroupPurchase.individualGroupPurchase;
import static com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.QIndividualGroupPurchaseProduct.individualGroupPurchaseProduct;

@RequiredArgsConstructor
public class IndividualGroupPurchaseRepositoryImpl implements IndividualGroupPurchaseRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<IndividualGroupPurchase> findByIdForUpdate(Long id) {
        IndividualGroupPurchase result = queryFactory
                .selectFrom(individualGroupPurchase)
                .where(individualGroupPurchase.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<IndividualGroupPurchase> findWithProductsByInviteCode(String inviteCode) {
        IndividualGroupPurchase result = queryFactory
                .selectFrom(individualGroupPurchase)
                .leftJoin(individualGroupPurchase.products, individualGroupPurchaseProduct).fetchJoin()
                .where(individualGroupPurchase.inviteCode.eq(inviteCode))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<IndividualGroupPurchase> findWithProductsById(Long id) {
        IndividualGroupPurchase result = queryFactory
                .selectFrom(individualGroupPurchase)
                .leftJoin(individualGroupPurchase.products, individualGroupPurchaseProduct).fetchJoin()
                .where(individualGroupPurchase.id.eq(id))
                .fetchOne();
        return Optional.ofNullable(result);
    }
}
