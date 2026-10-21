package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTypeAndOrderIdAndMemberId(PaymentType type, Long orderId, Long memberId);

    // 공동 주문 취소 시 환불 대상 (결제 완료·일부 환불 상태)
    List<Payment> findAllByTypeAndOrderIdAndStatusIn(PaymentType type, Long orderId, Collection<PaymentStatus> statuses);
}