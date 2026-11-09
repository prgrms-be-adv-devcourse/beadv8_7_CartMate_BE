package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySalesPostId(Long salesPostId);

    // 상품 단건 삭제: 상품을 영속성 컨텍스트에 올리지 않고 판매글 ID만 읽는다(뒤의 잠금 조회가 최신 상태로 읽어 오게)
    @Query("select p.salesPostId from Product p where p.id = :id")
    Optional<Long> findSalesPostIdById(@Param("id") Long id);

    // 상품 단건 삭제: 같은 판매글의 상품을 잠근다. 동시에 두 상품을 지워 판매글에 상품이 0개가 되는 걸 막는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.salesPostId = :salesPostId")
    List<Product> findForUpdateBySalesPostId(@Param("salesPostId") Long salesPostId);
}
