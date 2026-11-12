package com.cartmate.cartmate.boundedContext.etcService.delivery.out;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliverySellerCancel;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface DeliverySellerCancelRepository extends JpaRepository<DeliverySellerCancel, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO delivery_seller_cancel (order_id, purchase_type, seller_id, created_at) "
            + "VALUES (:orderId, :purchaseType, :sellerId, :now)", nativeQuery = true)
    int insertIfAbsent(@Param("orderId") Long orderId,
                       @Param("purchaseType") String purchaseType,
                       @Param("sellerId") Long sellerId,
                       @Param("now") LocalDateTime now);

    boolean existsByOrderIdAndPurchaseTypeAndSellerId(Long orderId, PurchaseType purchaseType, Long sellerId);
}
