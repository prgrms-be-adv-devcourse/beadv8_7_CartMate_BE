package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    // 주문과 결제로 홀딩 한 건을 조회한다. 구매자 한 명의 결제가 홀딩 한 건이다.
    Optional<Holding> findByOrderIdAndPaymentId(Long orderId, Long PaymentId);

    // 주문의 홀딩을 한 건으로 조회한다. 공동구매 주문은 참가자마다 홀딩이 있어 여러 건이면 오류가 나므로 쓰지 않는다.
    Optional<Holding> findByOrderId(Long orderId);

    // 주문의 홀딩을 ID 순으로 잠그고 조회한다. 개인 주문과 공동물품 주문은 주문 ID가 겹칠 수 있어 구매 유형까지 같이 조회한다.
    // 같은 홀딩에서 정산이 동시에 꺼내지 못하도록 트랜잭션 안에서 호출하며, 커밋할 때 홀딩 버전이 올라가 같은 시점의 환불도 충돌로 막힌다.
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select h from Holding h where h.orderId = :orderId and h.purchaseType = :purchaseType order by h.id")
    List<Holding> findAllByOrderIdAndPurchaseTypeForUpdate(
            @Param("orderId") Long orderId, @Param("purchaseType") WalletPurchaseType purchaseType);

    // 지갑의 해당 상태 홀딩 금액에서 환불로 돌려준 금액을 뺀 합계. 정산으로 꺼낸 금액은 빠져 있지 않아 따로 뺀다. 홀딩이 없으면 0이다.
    @Query("select coalesce(sum(h.amount - h.refundedAmount), 0) from Holding h where h.wallet.id = :walletId and h.status = :status")
    long sumUnrefundedAmountByWalletIdAndStatus(@Param("walletId") Long walletId, @Param("status") HoldingStatus status);
}
