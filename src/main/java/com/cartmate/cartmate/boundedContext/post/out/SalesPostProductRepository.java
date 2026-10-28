package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesPostProductRepository extends JpaRepository<SalesPostProduct, Long> {
    List<SalesPostProduct> findByIdIn(List<Long> productIds);

    @Query("select p.id from SalesPostProduct p where p.salesPost.id = :postId")
    List<Long> findIdsBySalesPostId(@Param("postId") Long postId);
}


