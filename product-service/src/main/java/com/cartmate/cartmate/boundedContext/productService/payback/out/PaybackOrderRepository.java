package com.cartmate.cartmate.boundedContext.productService.payback.out;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaybackOrderRepository extends JpaRepository<PaybackOrder, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_payback_order (order_id) VALUES (:orderId) "
            + "ON DUPLICATE KEY UPDATE order_id = order_id", nativeQuery = true)
    void insertIfAbsent(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaybackOrder o where o.orderId = :orderId")
    Optional<PaybackOrder> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("select o.individualGroupPurchaseId from PaybackOrder o where o.orderId = :orderId")
    Optional<Long> findIndividualGroupPurchaseId(@Param("orderId") Long orderId);

    List<PaybackOrder> findAllByIndividualGroupPurchaseId(Long individualGroupPurchaseId);
}
