package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTypeAndOrderIdAndMemberId(PaymentType type, Long orderId, Long memberId);
}