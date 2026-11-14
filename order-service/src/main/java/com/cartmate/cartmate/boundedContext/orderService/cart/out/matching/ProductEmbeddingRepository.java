package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

    @Query("""
            select e.productId from ProductEmbedding e
            where e.productId in :productIds and e.model = :model and e.textVersion = :textVersion
            """)
    List<Long> findUpToDateProductIds(@Param("productIds") Collection<Long> productIds,
                                      @Param("model") String model,
                                      @Param("textVersion") String textVersion);
}
