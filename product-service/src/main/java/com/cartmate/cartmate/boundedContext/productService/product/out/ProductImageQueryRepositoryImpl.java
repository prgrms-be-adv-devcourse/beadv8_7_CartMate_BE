package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.Collection;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.productService.product.domain.QProductImage.productImage;

public class ProductImageQueryRepositoryImpl implements ProductImageQueryRepository {
    private final JPAQueryFactory queryFactory;

    public ProductImageQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    // 키 목록을 한 번의 쿼리로 확인한다(파일마다 조회하지 않게)
    @Override
    public List<String> findExistingS3Keys(Collection<String> s3Keys) {
        if (s3Keys.isEmpty()) {
            return List.of();
        }
        return queryFactory
                .select(productImage.s3Key)
                .from(productImage)
                .where(productImage.s3Key.in(s3Keys))
                .fetch();
    }
}
