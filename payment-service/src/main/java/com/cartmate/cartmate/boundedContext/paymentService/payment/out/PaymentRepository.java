package com.cartmate.cartmate.boundedContext.paymentService.payment.out;

import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, PaymentRepositoryCustom {

    Optional<Payment> findByTypeAndOrderIdAndMemberId(PaymentType type, Long orderId, Long memberId);

    // 공동 주문 취소 시 환불 대상 (결제 완료·일부 환불 상태)
    List<Payment> findAllByTypeAndOrderIdAndStatusIn(PaymentType type, Long orderId, Collection<PaymentStatus> statuses);

    // 공동 주문 환불을 참여자 몫으로 나눌 대상 (환불 상태와 상관없이 전부)
    List<Payment> findAllByTypeAndOrderId(PaymentType type, Long orderId);

}