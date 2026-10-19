package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderIdAndMemberId(Long orderId, Long memberId);

    List<Payment> findAllByTypeAndOrderIdAndStatus(PaymentType type, Long orderId, PaymentStatus status);
}