package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTypeAndOrderIdAndMemberId(PaymentType type, Long orderId, Long memberId);

    // 공동 주문 취소 시 환불 대상 (결제 완료·일부 환불 상태)
    List<Payment> findAllByTypeAndOrderIdAndStatusIn(PaymentType type, Long orderId, Collection<PaymentStatus> statuses);

    @Query("select count(p) from Payment p where p.paidAt >= :from and p.paidAt < :to")
    long countPaidBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.paidAt >= :from and p.paidAt < :to")
    long sumPaidBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}