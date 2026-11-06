package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderStatus;
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

    // 개인물품 공동구매가 닫힐 때 취소할 결제 대기 주문 (idx_orders_individual_group_purchase_status)
    @Query("select o.id from Order o where o.individualGroupPurchaseId = :purchaseId and o.status = :status order by o.id")
    List<Long> findIdsByIndividualGroupPurchaseIdAndStatus(@Param("purchaseId") Long individualGroupPurchaseId,
                                                          @Param("status") OrderStatus status);

    List<Order> findAllByIndividualGroupPurchaseId(Long individualGroupPurchaseId);

    // 회원 탈퇴 확인: 구매자의 특정 상태 주문 수
    long countByBuyerMemberIdAndStatus(Long buyerMemberId, OrderStatus status);
}
