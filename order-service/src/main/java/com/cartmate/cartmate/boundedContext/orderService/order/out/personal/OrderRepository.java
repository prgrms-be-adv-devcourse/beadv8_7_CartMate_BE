package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.shared.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    boolean existsByOrderNumber(String orderNumber);

    @Query("select distinct o.id from Order o join o.items i " +
                       "where o.status = :status and i.productId in :productIds order by o.id")
    List<Long> findIdsByStatusAndProductIds(@Param("status") OrderStatus status,
                                            @Param("productIds") Collection<Long> productIds);

    @Query("select distinct o.id from Order o join o.items i " +
                       "where o.status in :statuses and i.productId in :productIds order by o.id")
    List<Long> findIdsByStatusInAndProductIds(@Param("statuses") Collection<OrderStatus> statuses,
                                              @Param("productIds") Collection<Long> productIds);

    @Query("select o.id from Order o where o.status = :status and o.cancelLastSentAt <= :before order by o.id")
    List<Long> findIdsByStatusAndCancelLastSentAtBefore(@Param("status") OrderStatus status,
                                                        @Param("before") LocalDateTime before);

    @Query("select o.id from Order o where o.individualGroupPurchaseId = :purchaseId and o.status = :status order by o.id")
    List<Long> findIdsByIndividualGroupPurchaseIdAndStatus(@Param("purchaseId") Long individualGroupPurchaseId,
                                                          @Param("status") OrderStatus status);

    List<Order> findAllByIndividualGroupPurchaseId(Long individualGroupPurchaseId);

    long countByBuyerMemberIdAndStatus(Long buyerMemberId, OrderStatus status);
}
