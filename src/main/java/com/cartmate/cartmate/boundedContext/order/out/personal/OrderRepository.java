package com.cartmate.cartmate.boundedContext.order.out.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    boolean existsByOrderNumber(String orderNumber);

    // 판매글 판매 중지 대상: 해당 상품이 들어 있는, 특정 상태의 주문
    @Query("select distinct o.id from Order o join o.items i " +
                       "where o.status = :status and i.productId in :productIds order by o.id")
    List<Long> findIdsByStatusAndProductIds(@Param("status") OrderStatus status,
                                            @Param("productIds") Collection<Long> productIds);
}
