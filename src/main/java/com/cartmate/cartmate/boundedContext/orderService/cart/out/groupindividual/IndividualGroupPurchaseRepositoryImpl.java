package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrigin;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.QIndividualGroupPurchase.individualGroupPurchase;
import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.QIndividualGroupPurchaseProduct.individualGroupPurchaseProduct;

public class IndividualGroupPurchaseRepositoryImpl implements IndividualGroupPurchaseRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public IndividualGroupPurchaseRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

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

    @Override
    public Optional<IndividualGroupPurchase> findByDemandProductKeyForUpdate(Long productId) {
        IndividualGroupPurchase result = queryFactory
                .selectFrom(individualGroupPurchase)
                .where(individualGroupPurchase.demandProductKey.eq(productId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Map<Long, Long> findOpenDemandPurchaseIds(Collection<Long> productIds, LocalDateTime now) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return queryFactory
                .select(individualGroupPurchase.demandProductKey, individualGroupPurchase.id)
                .from(individualGroupPurchase)
                .where(
                        individualGroupPurchase.demandProductKey.in(productIds),
                        individualGroupPurchase.status.eq(IndividualGroupPurchaseStatus.OPEN),
                        individualGroupPurchase.deadlineAt.gt(now)
                )
                .fetch().stream()
                .collect(Collectors.toMap(
                        row -> row.get(individualGroupPurchase.demandProductKey),
                        row -> row.get(individualGroupPurchase.id)
                ));
    }

    @Override
    public List<Long> findDemandProductIdsEndedBetween(LocalDateTime since, LocalDateTime now) {
        return queryFactory
                .select(individualGroupPurchase.sourceProductId)
                .distinct()
                .from(individualGroupPurchase)
                .where(
                        individualGroupPurchase.origin.eq(IndividualGroupPurchaseOrigin.ADMIN_DEMAND),
                        individualGroupPurchase.status.eq(IndividualGroupPurchaseStatus.CLOSED)
                                .and(individualGroupPurchase.closedAt.goe(since))
                                .or(individualGroupPurchase.status.eq(IndividualGroupPurchaseStatus.OPEN)
                                        .and(individualGroupPurchase.deadlineAt.goe(since))
                                        .and(individualGroupPurchase.deadlineAt.loe(now)))
                )
                .fetch();
    }

    @Override
    public List<IndividualGroupPurchase> findOpenDemandPurchases(LocalDateTime now) {
        return queryFactory
                .selectFrom(individualGroupPurchase)
                .where(
                        individualGroupPurchase.origin.eq(IndividualGroupPurchaseOrigin.ADMIN_DEMAND),
                        individualGroupPurchase.status.eq(IndividualGroupPurchaseStatus.OPEN),
                        individualGroupPurchase.deadlineAt.gt(now),
                        individualGroupPurchase.sourceProductId.isNotNull()
                )
                .orderBy(individualGroupPurchase.deadlineAt.asc(), individualGroupPurchase.id.asc())
                .fetch();
    }

    @Override
    public List<Long> findDemandIdsToCloseByDeadline(LocalDateTime now) {
        return queryFactory
                .select(individualGroupPurchase.id)
                .from(individualGroupPurchase)
                .where(
                        individualGroupPurchase.origin.eq(IndividualGroupPurchaseOrigin.ADMIN_DEMAND),
                        individualGroupPurchase.status.eq(IndividualGroupPurchaseStatus.OPEN),
                        individualGroupPurchase.deadlineAt.loe(now)
                )
                .orderBy(individualGroupPurchase.id.asc())
                .fetch();
    }
}
