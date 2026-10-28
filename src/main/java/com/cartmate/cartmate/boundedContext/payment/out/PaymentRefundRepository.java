package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    boolean existsByRefundKey(String refundKey);

    @Query("select count(r) from PaymentRefund r where r.createDate >= :from and r.createDate < :to")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(r.amount), 0) from PaymentRefund r where r.createDate >= :from and r.createDate < :to")
    long sumBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}