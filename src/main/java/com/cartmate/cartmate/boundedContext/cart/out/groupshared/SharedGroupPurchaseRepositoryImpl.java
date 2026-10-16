package com.cartmate.cartmate.boundedContext.cart.out.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.cart.domain.groupshared.QSharedGroupPurchase.sharedGroupPurchase;
import static com.cartmate.cartmate.boundedContext.cart.domain.groupshared.QSharedGroupPurchaseItem.sharedGroupPurchaseItem;
import static com.cartmate.cartmate.boundedContext.cart.domain.groupshared.QSharedGroupPurchaseMember.sharedGroupPurchaseMember;


@RequiredArgsConstructor
public class SharedGroupPurchaseRepositoryImpl implements SharedGroupPurchaseRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<SharedGroupPurchase> findByIdForUpdate(Long id) {
        SharedGroupPurchase result = queryFactory
                .selectFrom(sharedGroupPurchase)
                .where(sharedGroupPurchase.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<SharedGroupPurchase> findByInviteCodeForUpdate(String inviteCode) {
        SharedGroupPurchase result = queryFactory
                .selectFrom(sharedGroupPurchase)
                .where(sharedGroupPurchase.inviteCode.eq(inviteCode))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }


    @Override
    public Optional<SharedGroupPurchase> findWithDetailsById(Long id) {
        SharedGroupPurchase result = queryFactory
                .selectFrom(sharedGroupPurchase)
                .leftJoin(sharedGroupPurchase.members, sharedGroupPurchaseMember).fetchJoin()
                .where(sharedGroupPurchase.id.eq(id))
                .fetchOne();
        if (result == null) {
            return Optional.empty();
        }
        queryFactory
                .selectFrom(sharedGroupPurchase)
                .leftJoin(sharedGroupPurchase.items, sharedGroupPurchaseItem).fetchJoin()
                .where(sharedGroupPurchase.id.eq(id))
                .fetchOne();
        return Optional.of(result);
    }


    @Override
    public List<SharedGroupPurchase> findAllByParticipant(Long memberId) {
        return queryFactory
                .selectFrom(sharedGroupPurchase)
                .join(sharedGroupPurchase.members, sharedGroupPurchaseMember)
                .where(sharedGroupPurchaseMember.memberId.eq(memberId))
                .orderBy(sharedGroupPurchase.id.desc())
                .fetch();
    }


    @Override
    public List<Long> findIdsToCloseByDeadline(LocalDateTime now) {
        return queryFactory
                .select(sharedGroupPurchase.id)
                .from(sharedGroupPurchase)
                .where(
                        sharedGroupPurchase.status.eq(SharedGroupPurchaseStatus.OPEN),
                        sharedGroupPurchase.editDeadlineAt.loe(now)
                )
                .fetch();
    }


    @Override
    public List<Long> findIdsToReleaseFrozen(LocalDateTime now) {
        return queryFactory
                .select(sharedGroupPurchase.id)
                .from(sharedGroupPurchase)
                .where(
                        sharedGroupPurchase.status.eq(SharedGroupPurchaseStatus.FROZEN),
                        sharedGroupPurchase.frozenAt.loe(now.minus(SharedGroupPurchase.FROZEN_PERIOD))
                )
                .fetch();
    }
}
