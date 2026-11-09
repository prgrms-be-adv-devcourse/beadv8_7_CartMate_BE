package com.cartmate.cartmate.boundedContext.etcService.delivery.out;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryOrderState;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryOrderStateRepository extends JpaRepository<DeliveryOrderState, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO delivery_order_state (order_id, purchase_type, status, created_at, updated_at) "
            + "VALUES (:orderId, :purchaseType, :status, NOW(6), NOW(6))", nativeQuery = true)
    int insertIfAbsent(@Param("orderId") Long orderId,
                       @Param("purchaseType") String purchaseType,
                       @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DeliveryOrderState s where s.orderId = :orderId and s.purchaseType = :purchaseType")
    Optional<DeliveryOrderState> findForUpdate(@Param("orderId") Long orderId,
                                               @Param("purchaseType") PurchaseType purchaseType);
}
