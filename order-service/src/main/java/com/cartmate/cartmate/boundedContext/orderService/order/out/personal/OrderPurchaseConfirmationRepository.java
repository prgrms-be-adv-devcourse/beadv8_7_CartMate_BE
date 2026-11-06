package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderPurchaseConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OrderPurchaseConfirmationRepository extends JpaRepository<OrderPurchaseConfirmation, Long> {

    @Modifying
    @Query(value = "INSERT INTO order_purchase_confirmation (order_id, confirmed_at) VALUES (:orderId, :confirmedAt) "
            + "ON DUPLICATE KEY UPDATE order_id = order_id", nativeQuery = true)
    int insertIfAbsent(@Param("orderId") Long orderId, @Param("confirmedAt") LocalDateTime confirmedAt);
}
