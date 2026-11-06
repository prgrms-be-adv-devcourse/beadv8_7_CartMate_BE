package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IndividualGroupPurchaseReplicaRepository extends JpaRepository<IndividualGroupPurchaseReplica, Long> {

    // order 스키마 안의 자기 사본 행 락. 상품 목록은 락을 잡은 뒤 지연 로딩으로 읽는다(락과 같은 문장에서 조인하면 락 전 스냅샷)
    // 쓰는 곳은 릴레이 consumer뿐이고 릴레이는 ShedLock으로 한 인스턴스 · 한 줄씩 돈다. 없는 행을 잠그면 gap lock이 걸리지만
    // 이 전제에서는 겹치지 않는다(락이 만료돼 두 인스턴스가 겹치면 교착 · PK 충돌 → 릴레이 재시도로 수렴)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IndividualGroupPurchaseReplica r where r.individualGroupPurchaseId = :id")
    Optional<IndividualGroupPurchaseReplica> findByIdForUpdate(@Param("id") Long id);
}
