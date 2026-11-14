package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    // 구매용 조회: 상품을 함께 읽는다(상품명 · 판매 상태 · 판매자)
    @EntityGraph(attributePaths = "product")
    List<Inventory> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "product")
    List<Inventory> findAllByProduct_IdInAndStatusOrderByIdAsc(Collection<Long> productIds, ProductStatus status);

    // 선점: 남은 수량(재고 - 선점)이 충분할 때만 선점 수량을 늘린다. 바뀐 행 수 0 = 부족
    // UPDATE 하는 동안 DB가 이 행을 잠가서 동시에 선점해도 초과 판매가 생기지 않는다(별도 락 없음)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.reservedQuantity = i.reservedQuantity + :quantity "
            + "where i.id = :id and i.stockQuantity - i.reservedQuantity >= :quantity")
    int reserve(@Param("id") Long id, @Param("quantity") int quantity);

    // 선점 해제: 선점 수량을 줄인다. 바뀐 행 수 0 = 선점 수량이 모자람(기록과 어긋남)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.reservedQuantity = i.reservedQuantity - :quantity "
            + "where i.id = :id and i.reservedQuantity >= :quantity")
    int release(@Param("id") Long id, @Param("quantity") int quantity);

    // 판매 확정: 선점했던 수량을 재고와 선점에서 함께 뺀다(남은 수량은 그대로). 바뀐 행 수 0 = 기록과 어긋남
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.stockQuantity = i.stockQuantity - :quantity, "
            + "i.reservedQuantity = i.reservedQuantity - :quantity "
            + "where i.id = :id and i.reservedQuantity >= :quantity and i.stockQuantity >= :quantity")
    int confirm(@Param("id") Long id, @Param("quantity") int quantity);

    // 재고 복구: 결제 후 취소된 수량을 재고에 되돌린다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.stockQuantity = i.stockQuantity + :quantity where i.id = :id")
    int restore(@Param("id") Long id, @Param("quantity") int quantity);
}
