package com.cartmate.cartmate.boundedContext.paymentService.wallet.out;

import com.cartmate.cartmate.boundedContext.paymentService.wallet.domain.Holding;
import com.cartmate.cartmate.boundedContext.paymentService.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    // 주문과 결제로 홀딩 한 건을 조회한다. 구매자 한 명의 결제가 홀딩 한 건이다.
    Optional<Holding> findByOrderIdAndPaymentId(Long orderId, Long PaymentId);

    // 주문의 홀딩을 한 건으로 조회한다. 공동구매 주문은 참가자마다 홀딩이 있어 여러 건이면 오류가 나므로 쓰지 않는다.
    Optional<Holding> findByOrderId(Long orderId);

    // 주문의 홀딩을 ID 순으로 조회한다. 개인 주문과 공동물품 주문은 주문 ID가 겹칠 수 있어 구매 유형까지 같이 조회한다.
    // 같은 주문의 정산끼리는 주문 키 분산락(holding-order)으로 줄을 선다(ADR 0002·0009).
    @Query("select h from Holding h where h.orderId = :orderId and h.purchaseType = :purchaseType order by h.id")
    List<Holding> findAllByOrderIdAndPurchaseType(
            @Param("orderId") Long orderId, @Param("purchaseType") WalletPurchaseType purchaseType);

    // 지갑의 해당 상태 홀딩 금액에서 환불로 돌려준 금액을 뺀 합계. 정산으로 꺼낸 금액은 빠져 있지 않아 따로 뺀다. 홀딩이 없으면 0이다.
    @Query("select coalesce(sum(h.amount - h.refundedAmount), 0) from Holding h where h.wallet.id = :walletId and h.status = :status")
    long sumUnrefundedAmountByWalletIdAndStatus(@Param("walletId") Long walletId, @Param("status") HoldingStatus status);
}
