package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Delivery> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

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
