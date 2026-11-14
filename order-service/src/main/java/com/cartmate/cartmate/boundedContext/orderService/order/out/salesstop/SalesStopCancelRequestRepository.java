package com.cartmate.cartmate.boundedContext.orderService.order.out.salesstop;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop.SalesStopCancelRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop.SalesStopCancelRequestStatus;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SalesStopCancelRequestRepository extends JpaRepository<SalesStopCancelRequest, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO sales_stop_cancel_request "
            + "(purchase_type, order_id, seller_id, status, requested_at, last_sent_at) "
            + "VALUES (:purchaseType, :orderId, :sellerId, 'REQUESTED', :now, :now)", nativeQuery = true)
    int insertIfAbsent(@Param("purchaseType") String purchaseType,
                       @Param("orderId") Long orderId,
                       @Param("sellerId") Long sellerId,
                       @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SalesStopCancelRequest r "
            + "where r.purchaseType = :purchaseType and r.orderId = :orderId and r.sellerId = :sellerId")
    Optional<SalesStopCancelRequest> findForUpdate(@Param("purchaseType") PurchaseType purchaseType,
                                                   @Param("orderId") Long orderId,
                                                   @Param("sellerId") Long sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SalesStopCancelRequest r where r.id = :id")
    Optional<SalesStopCancelRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.id from SalesStopCancelRequest r "
            + "where r.status = :status and r.lastSentAt <= :lastSentBefore order by r.id")
    List<Long> findIdsByStatusAndLastSentAtBefore(@Param("status") SalesStopCancelRequestStatus status,
                                                  @Param("lastSentBefore") LocalDateTime lastSentBefore);
}
