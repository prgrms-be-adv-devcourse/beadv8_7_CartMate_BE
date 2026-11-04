package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.order.out.OrderCartClient;
import com.cartmate.cartmate.boundedContext.order.out.personal.IndividualGroupPurchaseReplicaRepository;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
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
