package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCartClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.IndividualGroupPurchaseReplicaRepository;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseReplicaUseCase {

    private final IndividualGroupPurchaseReplicaRepository replicaRepository;
    private final OrderCartClient orderCartClient;
    private final EntityManager entityManager;

    void applyOpened(IndividualGroupPurchaseOpenedEvent event) {
        applyOpenState(event.individualGroupPurchaseId(), event.deadlineAt(), event.productIds(), event.revision());
    }

    void applyUpdated(IndividualGroupPurchaseUpdatedEvent event) {
        applyOpenState(event.individualGroupPurchaseId(), event.deadlineAt(), event.productIds(), event.revision());
    }

    // 열림보다 종료가 먼저 와도(재시도로 밀림) 닫힌 사본을 만든다. 늦게 온 열림은 닫힌 사본을 다시 열지 못한다
    void applyClosed(IndividualGroupPurchaseClosedEvent event) {
        replicaRepository.findByIdForUpdate(event.individualGroupPurchaseId())
                .ifPresentOrElse(
                        replica -> replica.applyClosed(event.deadlineAt(), event.closedAt(), event.revision()),
                        () -> replicaRepository.save(IndividualGroupPurchaseReplica.of(event.individualGroupPurchaseId(),
                                event.deadlineAt(), true, event.closedAt(), List.of(), event.revision())));
    }

    /**
     * 주문 생성 · 결제가 판단에 쓸 공동구매 상태. 보통은 사본이다.
     * 사본이 없으면(열림 이벤트가 아직 안 옴, 이 기능 배포 전부터 있던 공동구매) cart에 잠그지 않고 물어 판단만 한다.
     * 이때 받은 상태는 저장하지 않는다. 사본은 이벤트 consumer만 쓴다(번호 비교를 한곳에서만 하려고).
     */
    Optional<IndividualGroupPurchaseReplica> find(Long individualGroupPurchaseId) {
        return replicaRepository.findById(individualGroupPurchaseId)
                .or(() -> orderCartClient.findIndividualGroupPurchaseSnapshot(individualGroupPurchaseId)
                        .map(snapshot -> IndividualGroupPurchaseReplica.of(snapshot.individualGroupPurchaseId(),
                                snapshot.deadlineAt(), snapshot.closed(), snapshot.closedAt(), snapshot.productIds(),
                                snapshot.revision())));
    }

    /**
     * 결제 반영용. {@link #find}와 같지만 사본 행을 공유 락으로 다시 읽어(current read) 최신 닫힘 · 마감 시각을 본다.
     * 결제 트랜잭션은 payment가 열고 앞서 다른 조회를 하므로 일반 읽기는 그때의 스냅샷이라, 종료 consumer가 사본을 닫고 커밋한 뒤에도
     * "열림"으로 보일 수 있다(ARCH-17 리뷰). 공유 락은 사본 닫기(배타 락)가 끝나기를 기다렸다 읽는다.
     * order 스키마 안의 자기 행이라 모듈을 넘는 락이 아니고, consumer는 사본 락과 주문 락을 함께 쥐지 않아 교착이 없다.
     * 상품 목록은 지연 로딩이라 스냅샷 값일 수 있다(상품 제거와 결제가 겹치는 좁은 틈, 받아들임).
     */
    Optional<IndividualGroupPurchaseReplica> findForPayment(Long individualGroupPurchaseId) {
        Optional<IndividualGroupPurchaseReplica> replica = replicaRepository.findById(individualGroupPurchaseId);
        if (replica.isEmpty()) {
            return find(individualGroupPurchaseId);
        }
        entityManager.refresh(replica.get(), LockModeType.PESSIMISTIC_READ);
        return replica;
    }

    // 릴레이는 보통 한 인스턴스에서 한 줄씩 넘긴다. 두 인스턴스가 같은 행을 동시에 만들면 한쪽이 PK 충돌로 실패하고,
    // 릴레이가 다시 시도할 때 만들어진 행을 잠가 번호를 비교하므로 같은 결과로 수렴한다
    private void applyOpenState(Long individualGroupPurchaseId, LocalDateTime deadlineAt,
                                List<Long> productIds, long revision) {
        replicaRepository.findByIdForUpdate(individualGroupPurchaseId)
                .ifPresentOrElse(
                        replica -> replica.applyOpenState(deadlineAt, productIds, revision),
                        () -> replicaRepository.save(IndividualGroupPurchaseReplica.of(individualGroupPurchaseId,
                                deadlineAt, false, null, productIds, revision)));
    }
}
