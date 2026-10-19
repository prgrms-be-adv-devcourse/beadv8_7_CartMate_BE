package com.cartmate.cartmate.boundedContext.payout.out;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    List<Payout> findAllByOrderItemIdIn(Collection<Long> orderItemIds);

    // 지급 예정 시각이 지난 대기 정산의 ID를 lastId 다음부터 ID 순으로 조회한다. 지급 배치가 페이지 단위로 읽는 데 쓴다.
    @Query("select p.id from Payout p where p.status = :status and p.scheduledAt <= :now and p.id > :lastId order by p.id")
    List<Long> findDueIdsAfter(
            @Param("status") PayoutStatus status, @Param("now") LocalDateTime now,
            @Param("lastId") Long lastId, Pageable pageable);

    // 지급 예정 시각이 지난 대기 정산의 ID 목록. 지급 배치 대상 조회에 쓴다.
    @Query("select p.id from Payout p where p.status = :status and p.scheduledAt <= :now order by p.id")
    List<Long> findIdsByStatusAndScheduledAtBefore(
            @Param("status") PayoutStatus status, @Param("now") LocalDateTime now);

    // 정산 행을 잠그고 조회한다. 지급이 동시에 들어와도 한 건만 처리되도록 트랜잭션 안에서 호출한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") Long id);
}
