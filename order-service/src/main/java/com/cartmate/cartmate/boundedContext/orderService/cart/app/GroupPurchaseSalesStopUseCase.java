package com.cartmate.cartmate.boundedContext.orderService.cart.app;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseEvents;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseCancelReason;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseEvents;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupPurchaseSalesStopUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> findIndividualTargets(List<Long> productIds) {
        return productIds.isEmpty() ? List.of() : individualGroupPurchaseRepository.findOpenIdsByProductIds(productIds);
    }

    @Transactional(readOnly = true)
    public List<Long> findSharedTargets(List<Long> productIds) {
        return productIds.isEmpty() ? List.of() : sharedGroupPurchaseRepository.findEditableIdsByProductIds(productIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeFromIndividual(Long groupPurchaseId, List<Long> productIds) {
        individualGroupPurchaseRepository.findByIdForUpdate(groupPurchaseId).ifPresent(purchase -> {
            boolean changed = purchase.removeProductsBySalesStopped(new HashSet<>(productIds), LocalDateTime.now(clock));
            if (!changed) {
                return;
            }
            eventPublisher.publish(purchase.isClosed()
                    ? IndividualGroupPurchaseEvents.closed(purchase, IndividualGroupPurchaseCloseReason.SALES_STOPPED)
                    : IndividualGroupPurchaseEvents.updated(purchase, LocalDateTime.now(clock)));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeFromShared(Long groupPurchaseId, List<Long> productIds) {
        sharedGroupPurchaseRepository.findByIdForUpdate(groupPurchaseId).ifPresent(purchase -> {
            boolean changed = purchase.removeItemsBySalesStopped(new HashSet<>(productIds), LocalDateTime.now(clock));
            if (changed && purchase.isCanceled()) {
                eventPublisher.publish(
                        SharedGroupPurchaseEvents.canceled(purchase, SharedGroupPurchaseCancelReason.SALES_STOPPED));
            }
        });
    }
}