package com.cartmate.cartmate.boundedContext.etcService.delivery.out;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryClaimRepository extends JpaRepository<DeliveryClaim, Long> {

    // 클레임 행을 잠그고 조회한다. 환불 결과 반영(이벤트 경로, Inbox 트랜잭션 안)이 쓴다. 승인·거절·완료는 배송 키 분산락을 쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeliveryClaim c where c.id = :id")
    Optional<DeliveryClaim> findByIdForUpdate(@Param("id") Long id);

    // 클레임의 배송 ID. 클레임 처리 전에 배송 키 분산락을 만들 때 쓴다.
    @Query("select c.delivery.id from DeliveryClaim c where c.id = :id")
    Optional<Long> findDeliveryIdById(@Param("id") Long id);

    Page<DeliveryClaim> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    // 지정한 상태의 클레임이 배송에 하나라도 있는지 확인한다.
    boolean existsByDeliveryIdAndStatusIn(Long deliveryId, Collection<ClaimStatus> statuses);

    // 판매자가 아직 처리하지 않았거나(접수) 승인해 진행 중인 클레임이 배송에 있는지 확인한다. 이런 배송은 구매확정할 수 없다.
    default boolean hasActiveClaim(Long deliveryId) {
        return existsByDeliveryIdAndStatusIn(deliveryId, List.of(ClaimStatus.REQUESTED, ClaimStatus.APPROVED));
    }

    // 판매자의 배송에 접수된 클레임을 상태·유형으로 걸러 최신순으로 조회한다. 배송은 주문번호 표시를 위해 함께 가져온다.
    @Query(value = "select c from DeliveryClaim c join fetch c.delivery d "
            + "where d.sellerId = :sellerId and c.status in :statuses and c.claimType in :claimTypes "
            + "order by c.createdAt desc, c.id desc",
            countQuery = "select count(c) from DeliveryClaim c "
                    + "where c.delivery.sellerId = :sellerId and c.status in :statuses and c.claimType in :claimTypes")
    Page<DeliveryClaim> findSellerClaims(
            @Param("sellerId") Long sellerId,
            @Param("statuses") Collection<ClaimStatus> statuses,
            @Param("claimTypes") Collection<ClaimType> claimTypes,
            Pageable pageable);

    // 회원 탈퇴 확인: 구매자·판매자의 처리 중인 클레임 수
    long countByMemberIdAndStatusIn(Long memberId, Collection<ClaimStatus> statuses);

    long countByDeliverySellerIdAndStatusIn(Long sellerId, Collection<ClaimStatus> statuses);
}
