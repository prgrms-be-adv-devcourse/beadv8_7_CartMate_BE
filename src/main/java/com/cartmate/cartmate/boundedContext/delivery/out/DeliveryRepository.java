package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    // 배송 행을 잠그고 조회한다. 구매확정이 동시에 들어와도 한 건만 처리되도록 트랜잭션 안에서 호출한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);

    // 한 주문의 배송을 모두 잠그고 조회한다. 주문 취소와 판매자의 배송준비 처리가 동시에 들어와도 한쪽만 성공하게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.orderId = :orderId and d.purchaseType = :purchaseType order by d.id")
    List<Delivery> findAllByOrderIdAndPurchaseTypeForUpdate(
            @Param("orderId") Long orderId, @Param("purchaseType") PurchaseType purchaseType);

    Optional<Delivery> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Delivery> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    // 한 주문의 배송을 구매자 본인 것만 최신순으로 조회한다. 주문 ID는 구매 유형별로 겹칠 수 있어 유형도 함께 건다.
    List<Delivery> findByMemberIdAndOrderIdAndPurchaseTypeOrderByCreatedAtDescIdDesc(
            Long memberId, Long orderId, PurchaseType purchaseType);

    // 지정한 시각 이전에 해당 상태가 된 배송의 ID 목록. 배송중 자동 완료 처리 대상 조회에 쓴다.
    @Query("select d.id from Delivery d where d.status = :status and d.shippedAt < :before order by d.id")
    List<Long> findIdsByStatusAndShippedAtBefore(
            @Param("status") DeliveryStatus status, @Param("before") LocalDateTime before);

    // 지정한 시각 이전에 배송완료된 배송의 ID 목록. 자동 구매확정 대상 조회에 쓴다.
    @Query("select d.id from Delivery d where d.status = :status and d.deliveredAt < :before order by d.id")
    List<Long> findIdsByStatusAndDeliveredAtBefore(
            @Param("status") DeliveryStatus status, @Param("before") LocalDateTime before);

    Page<Delivery> findBySellerIdAndStatusOrderByCreatedAtDescIdDesc(
            Long sellerId, DeliveryStatus status, Pageable pageable);
}
