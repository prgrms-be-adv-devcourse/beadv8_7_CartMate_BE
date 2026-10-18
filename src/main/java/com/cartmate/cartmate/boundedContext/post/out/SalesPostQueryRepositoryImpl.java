package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import static com.cartmate.cartmate.boundedContext.post.domain.QSalesPost.salesPost;
import java.util.List;

@RequiredArgsConstructor
public class SalesPostQueryRepositoryImpl implements SalesPostQueryRepository {
    private final JPAQueryFactory queryFactory;

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

//    private BooleanExpression postIdEq(Long postId) {
//        return postId == null ? null : salesPost.id.eq(postId);
//    }

    private BooleanExpression notDeleted() {
        return salesPost.status.ne(SalesPostSaleStatus.DELETED);
    }

}
