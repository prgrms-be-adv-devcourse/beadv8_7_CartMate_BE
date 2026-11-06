package com.cartmate.cartmate.boundedContext.productService.payback.out;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.IndividualGroupPurchasePayback;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IndividualGroupPurchasePaybackRepository
        extends JpaRepository<IndividualGroupPurchasePayback, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_individual_group_purchase_payback "
            + "(individual_group_purchase_id, opened, closed, revision) VALUES (:id, false, false, 0) "
            + "ON DUPLICATE KEY UPDATE individual_group_purchase_id = individual_group_purchase_id",
            nativeQuery = true)
    void insertIfAbsent(@Param("id") Long individualGroupPurchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from IndividualGroupPurchasePayback p where p.individualGroupPurchaseId = :id")
    Optional<IndividualGroupPurchasePayback> findByIdForUpdate(@Param("id") Long individualGroupPurchaseId);
}
