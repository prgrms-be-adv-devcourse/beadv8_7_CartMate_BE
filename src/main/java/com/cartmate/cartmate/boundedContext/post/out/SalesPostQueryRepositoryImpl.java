package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.post.domain.QSalesPost.salesPost;

public class SalesPostQueryRepositoryImpl implements SalesPostQueryRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager em;

    public SalesPostQueryRepositoryImpl(EntityManager entityManager) {
        this.em = entityManager;
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<SalesPost> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable) {
        List<SalesPost> content = queryFactory
                .selectFrom(salesPost)
                .where(
                        titleContains(request.getTitle()),
                        notDeleted()
                )
                .orderBy(salesPost.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> countSalesPost(request));
    }

    private long countSalesPost(SelectSalesPostRequest request) {
        Long total = queryFactory
                .select(salesPost.count())
                .from(salesPost)
                .where(
                        titleContains(request.getTitle()),
                        notDeleted()
                )
                .fetchOne();
        return total == null ? 0L : total;
    }

    private BooleanExpression titleContains(String title) {
        return title == null || title.isBlank() ? null : salesPost.title.contains(title);
    }

    private BooleanExpression notDeleted() {
        return salesPost.status.notIn(SalesPostSaleStatus.DELETED, SalesPostSaleStatus.SUSPENDED);
    }

    @Override
    public Long changeUpdateStatus(Long salesPostId, Collection<SalesPostUpdateStatus> from,
                                   SalesPostUpdateStatus to, LocalDateTime now) {
        long updated = queryFactory
                .update(salesPost)
                .set(salesPost.updateStatus, to)
                .set(salesPost.updateStatusChangedAt, now)
                .where(salesPost.id.eq(salesPostId), salesPost.updateStatus.in(from))
                .execute();

        em.flush();
        em.clear();
        return updated;
    }

    @Override
    public Long startUpdate(Long salesPostId, Collection<SalesPostUpdateStatus> from, LocalDateTime now) {
        long updated = queryFactory
                .update(salesPost)
                .set(salesPost.updateStatus, SalesPostUpdateStatus.PENDING)
                .set(salesPost.updateStatusChangedAt, now)
                .where(
                        salesPost.id.eq(salesPostId),
                        salesPost.updateStatus.in(from),
                        salesPost.status.notIn(SalesPostSaleStatus.PENDING, SalesPostSaleStatus.DELETED)
                )
                .execute();

        em.flush();
        em.clear();
        return updated;
    }

    @Override
    public Long startDelete(Long salesPostId, Collection<SalesPostUpdateStatus> editable) {
        long updated = queryFactory
                .update(salesPost)
                .set(salesPost.status, SalesPostSaleStatus.PENDING)
                .where(
                        salesPost.id.eq(salesPostId),
                        salesPost.updateStatus.in(editable),
                        salesPost.status.notIn(SalesPostSaleStatus.PENDING, SalesPostSaleStatus.DELETED)
                )
                .execute();

        em.flush();
        em.clear();
        return updated;
    }

    @Override
    public Page<SalesPost> selectForAdmin(SalesPostSaleStatus status, String keyword, Pageable pageable) {
        List<SalesPost> content = queryFactory
                .selectFrom(salesPost)
                .where(adminVisible(), statusEq(status), titleContains(keyword))
                .orderBy(salesPost.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> {
            Long total = queryFactory
                    .select(salesPost.count())
                    .from(salesPost)
                    .where(adminVisible(), statusEq(status), titleContains(keyword))
                    .fetchOne();
            return total == null ? 0L : total;
        });
    }

    // 관리자는 삭제·등록 진행 중을 제외한 전부를 본다 (숨김 포함)
    private BooleanExpression adminVisible() {
        return salesPost.status.notIn(SalesPostSaleStatus.DELETED, SalesPostSaleStatus.PENDING);
    }

    private BooleanExpression statusEq(SalesPostSaleStatus status) {
        return status == null ? null : salesPost.status.eq(status);
    }

}
