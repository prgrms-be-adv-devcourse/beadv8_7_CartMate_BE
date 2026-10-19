package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
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

    // 주문의 홀딩을 ID 순으로 잠그고 조회한다. 같은 홀딩에서 정산이 동시에 꺼내지 못하도록 트랜잭션 안에서 호출하며, 커밋할 때 홀딩 버전이 올라가 같은 시점의 환불도 충돌로 막힌다.
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select h from Holding h where h.orderId = :orderId order by h.id")
    List<Holding> findAllByOrderIdForUpdate(@Param("orderId") Long orderId);
}
