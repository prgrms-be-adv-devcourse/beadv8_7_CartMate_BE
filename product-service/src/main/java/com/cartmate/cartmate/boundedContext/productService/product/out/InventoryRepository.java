package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    // 구매용 조회: 상품을 함께 읽는다(상품명 · 판매 상태 · 판매자)
    @EntityGraph(attributePaths = "product")
    List<Inventory> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "product")
    List<Inventory> findAllByProduct_IdInAndStatusOrderByIdAsc(Collection<Long> productIds, ProductStatus status);
}
