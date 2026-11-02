package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.member.domain.MemberStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;

import static com.cartmate.cartmate.boundedContext.member.domain.QMember.member;

public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public MemberRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    // 조건별 개수를 SUM(CASE WHEN ...)으로 한 번에 센다.
    // select count(*), sum(case when create_date >= ? then 1 else 0 end), sum(case when role = 'BUYER' ...) ... from member
    @Override
    public MemberCounts countForDashboard(LocalDateTime todayStart) {
        NumberExpression<Long> total = member.count();
        NumberExpression<Long> joinedToday = countIf(member.createDate.goe(todayStart));
        NumberExpression<Long> buyers = countIf(member.role.eq(MemberRole.BUYER));
        NumberExpression<Long> sellers = countIf(member.role.eq(MemberRole.SELLER));
        NumberExpression<Long> suspended = countIf(member.status.eq(MemberStatus.SUSPENDED));

        Tuple row = queryFactory
                .select(total, joinedToday, buyers, sellers, suspended)
                .from(member)
                .fetchOne();

        return new MemberCounts(
                orZero(row, total),
                orZero(row, joinedToday),
                orZero(row, buyers),
                orZero(row, sellers),
                orZero(row, suspended));
    }

    private static NumberExpression<Long> countIf(BooleanExpression condition) {
        return new CaseBuilder().when(condition).then(1L).otherwise(0L).sumLong();
    }

    // 회원이 한 명도 없으면 SUM은 null이 된다
    private static long orZero(Tuple row, NumberExpression<Long> expression) {
        Long value = row == null ? null : row.get(expression);
        return value == null ? 0L : value;
    }
}