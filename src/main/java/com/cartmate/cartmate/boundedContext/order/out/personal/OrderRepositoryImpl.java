package com.cartmate.cartmate.boundedContext.order.out.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.order.domain.personal.QOrder.order;
import static com.cartmate.cartmate.boundedContext.order.domain.personal.QOrderItem.orderItem;

public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public OrderRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<Order> findByIdForUpdate(Long id) {
        Order result = queryFactory
                .selectFrom(order)
                .where(order.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Order> findWithItemsById(Long id) {
        Order result = queryFactory
                .selectFrom(order)
                .leftJoin(order.items, orderItem).fetchJoin()
                .where(order.id.eq(id))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<Order> findAllByMember(Long memberId) {
        return queryFactory
                .selectFrom(order)
                .distinct()
                .leftJoin(order.items, orderItem).fetchJoin()
                .where(order.buyerMemberId.eq(memberId))
                .orderBy(order.id.desc())
                .fetch();
    }

    @Override
    public List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return queryFactory
                .select(order.id)
                .from(order)
                .where(
                        order.status.eq(OrderStatus.PAYMENT_PENDING),
                        order.paymentDeadlineAt.loe(now)
                )
                .fetch();
    }
}
