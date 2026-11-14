package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.IndividualGroupPurchasePayback;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackCondition;
import com.cartmate.cartmate.boundedContext.productService.payback.out.IndividualGroupPurchasePaybackRepository;
import com.cartmate.cartmate.boundedContext.productService.payback.out.PaybackConditionRepository;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class GroupPurchaseSnapshotUseCase {

    private final IndividualGroupPurchasePaybackRepository paybackRepository;
    private final PaybackConditionRepository conditionRepository;

    IndividualGroupPurchasePayback applyOpened(IndividualGroupPurchaseOpenedEvent event) {
        return applyOpenState(event.individualGroupPurchaseId(), event.deadlineAt(), event.productIds(),
                event.revision());
    }

    IndividualGroupPurchasePayback applyUpdated(IndividualGroupPurchaseUpdatedEvent event) {
        return applyOpenState(event.individualGroupPurchaseId(), event.deadlineAt(), event.productIds(),
                event.revision());
    }

    IndividualGroupPurchasePayback applyClosed(IndividualGroupPurchaseClosedEvent event) {
        IndividualGroupPurchasePayback payback = lock(event.individualGroupPurchaseId());
        payback.applyClosed(event.deadlineAt(), event.closedAt(), event.revision());
        return payback;
    }

    IndividualGroupPurchasePayback lock(Long individualGroupPurchaseId) {
        paybackRepository.insertIfAbsent(individualGroupPurchaseId);
        return paybackRepository.findByIdForUpdate(individualGroupPurchaseId).orElseThrow();
    }

    private IndividualGroupPurchasePayback applyOpenState(Long individualGroupPurchaseId, LocalDateTime deadlineAt,
                                                          List<Long> productIds, long revision) {
        IndividualGroupPurchasePayback payback = lock(individualGroupPurchaseId);
        Set<Long> entered = payback.applyOpenState(deadlineAt, productIds, revision);
        if (!entered.isEmpty()) {
            payback.snapshot(conditionRepository.findAllByProductIdIn(entered).stream()
                    .map(PaybackCondition::toSnapshot)
                    .toList());
        }
        return payback;
    }
}
