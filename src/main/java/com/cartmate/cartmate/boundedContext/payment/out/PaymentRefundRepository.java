package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    boolean existsByRefundKey(String refundKey);
}