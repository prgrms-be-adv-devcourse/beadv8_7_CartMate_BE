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

    void applyClosed(IndividualGroupPurchaseClosedEvent event) {
        replicaRepository.findByIdForUpdate(event.individualGroupPurchaseId())
                .ifPresentOrElse(
                        found -> found.applyClosed(event.deadlineAt(), event.closedAt(), event.revision()),
                        () -> replicaRepository.save(IndividualGroupPurchaseReplica.of(
                                event.individualGroupPurchaseId(), event.deadlineAt(), true, event.closedAt(),
                                List.of(), event.revision())));
    }

    Optional<IndividualGroupPurchaseReplica> find(Long individualGroupPurchaseId) {
        return replicaRepository.findById(individualGroupPurchaseId)
                .or(() -> orderCartClient.findIndividualGroupPurchaseSnapshot(individualGroupPurchaseId)
                        .map(snapshot -> IndividualGroupPurchaseReplica.of(snapshot.individualGroupPurchaseId(),
                                snapshot.deadlineAt(), snapshot.closed(), snapshot.closedAt(), snapshot.productIds(),
                                snapshot.revision())));
    }

    Optional<IndividualGroupPurchaseReplica> findForPayment(Long individualGroupPurchaseId) {
        Optional<IndividualGroupPurchaseReplica> replica = replicaRepository.findById(individualGroupPurchaseId);
        if (replica.isEmpty()) {
            return find(individualGroupPurchaseId);
        }
        entityManager.refresh(replica.get(), LockModeType.PESSIMISTIC_READ);
        return replica;
    }

    private IndividualGroupPurchaseReplica applyOpenState(Long individualGroupPurchaseId, LocalDateTime deadlineAt,
                                                          List<Long> productIds, long revision) {
        return replicaRepository.findByIdForUpdate(individualGroupPurchaseId)
                .map(replica -> {
                    replica.applyOpenState(deadlineAt, productIds, revision);
                    return replica;
                })
                .orElseGet(() -> replicaRepository.save(IndividualGroupPurchaseReplica.of(individualGroupPurchaseId,
                        deadlineAt, false, null, productIds, revision)));
    }
}
