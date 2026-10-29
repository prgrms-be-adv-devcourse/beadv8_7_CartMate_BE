package com.cartmate.cartmate.boundedContext.order.out.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.QSharedGroupOrderParticipant;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.order.domain.groupshared.QSharedGroupOrder.sharedGroupOrder;
import static com.cartmate.cartmate.boundedContext.order.domain.groupshared.QSharedGroupOrderItem.sharedGroupOrderItem;
import static com.cartmate.cartmate.boundedContext.order.domain.groupshared.QSharedGroupOrderParticipant.sharedGroupOrderParticipant;

public class SharedGroupOrderRepositoryImpl implements SharedGroupOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public SharedGroupOrderRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<SharedGroupOrder> findByIdForUpdate(Long id) {
        SharedGroupOrder result = queryFactory
                .selectFrom(sharedGroupOrder)
                .where(sharedGroupOrder.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<SharedGroupOrder> findWithDetailsById(Long id) {
        SharedGroupOrder result = queryFactory
                .selectFrom(sharedGroupOrder)
                .leftJoin(sharedGroupOrder.participants, sharedGroupOrderParticipant).fetchJoin()
                .where(sharedGroupOrder.id.eq(id))
                .fetchOne();
        if (result == null) {
            return Optional.empty();
        }
        queryFactory
                .selectFrom(sharedGroupOrder)
                .leftJoin(sharedGroupOrder.items, sharedGroupOrderItem).fetchJoin()
                .where(sharedGroupOrder.id.eq(id))
                .fetchOne();
        return Optional.of(result);
    }

    @Override
    public List<SharedGroupOrder> findAllByParticipant(Long memberId) {
        QSharedGroupOrderParticipant mine = new QSharedGroupOrderParticipant("mine");
        List<SharedGroupOrder> result = queryFactory
                .selectFrom(sharedGroupOrder)
                .distinct()
                .leftJoin(sharedGroupOrder.participants, sharedGroupOrderParticipant).fetchJoin()
                .where(JPAExpressions.selectOne()
                        .from(mine)
                        .where(mine.sharedGroupOrder.eq(sharedGroupOrder), mine.memberId.eq(memberId))
                        .exists())
                .orderBy(sharedGroupOrder.id.desc())
                .fetch();
        if (result.isEmpty()) {
            return result;
        }
        queryFactory
                .selectFrom(sharedGroupOrder)
                .distinct()
                .leftJoin(sharedGroupOrder.items, sharedGroupOrderItem).fetchJoin()
                .where(sharedGroupOrder.in(result))
                .fetch();
        return result;
    }

    @Override
    public List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return queryFactory
                .select(sharedGroupOrder.id)
                .from(sharedGroupOrder)
                .where(
                        sharedGroupOrder.status.eq(SharedGroupOrderStatus.PAYING),
                        sharedGroupOrder.paymentDeadlineAt.loe(now.minus(SharedGroupOrder.GRACE_PERIOD))
                )
                .fetch();
    }
}
