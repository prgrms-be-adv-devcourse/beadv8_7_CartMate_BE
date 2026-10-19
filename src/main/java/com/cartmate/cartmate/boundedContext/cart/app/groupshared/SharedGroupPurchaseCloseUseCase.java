package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseMember;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseStatus;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseCloseUseCase {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final ApplicationEventPublisher eventPublisher;

    void close(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId);
        Set<Long> unavailable = sharedGroupPurchaseSupport.findUnavailableInventoryIds(purchase);
        purchase.close(memberId, unavailable, LocalDateTime.now());
        publishClosedEventIfClosed(purchase);
    }

    void cancel(Long memberId, Long sharedGroupPurchaseId) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .cancel(memberId, LocalDateTime.now());
    }

    void closeByDeadline(Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId);
        LocalDateTime now = LocalDateTime.now();
        if (purchase.getStatus() == SharedGroupPurchaseStatus.OPEN && !purchase.isEditable(now)) {
            Set<Long> unavailable = sharedGroupPurchaseSupport.findUnavailableInventoryIds(purchase);
            purchase.closeByDeadline(unavailable, now);
            publishClosedEventIfClosed(purchase);
        }
    }

    void closeByFrozenTimeout(Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId);
        LocalDateTime now = LocalDateTime.now();
        boolean frozen = purchase.getStatus() == SharedGroupPurchaseStatus.FROZEN;
        if (frozen && !now.isBefore(purchase.getFrozenAt().plus(SharedGroupPurchase.FROZEN_PERIOD))) {
            Set<Long> unavailable = sharedGroupPurchaseSupport.findUnavailableInventoryIds(purchase);
            purchase.closeByFrozenTimeout(unavailable, now);
            publishClosedEventIfClosed(purchase);
        }
    }

    List<Long> findIdsToCloseByDeadline(LocalDateTime now) {
        return sharedGroupPurchaseRepository.findIdsToCloseByDeadline(now);
    }

    List<Long> findIdsToReleaseFrozen(LocalDateTime now) {
        return sharedGroupPurchaseRepository.findIdsToReleaseFrozen(now);
    }

    private void publishClosedEventIfClosed(SharedGroupPurchase purchase) {
        if (purchase.getStatus() == SharedGroupPurchaseStatus.CLOSED) {
            eventPublisher.publishEvent(toClosedEvent(purchase));
        }
    }

    private SharedGroupPurchaseClosedEvent toClosedEvent(SharedGroupPurchase purchase) {
        List<Long> participantMemberIds = purchase.getMembers().stream()
                .map(SharedGroupPurchaseMember::getMemberId)
                .toList();
        List<SharedGroupPurchaseClosedEvent.Item> items = purchase.getItems().stream()
                .map(item -> new SharedGroupPurchaseClosedEvent.Item(
                        item.getProductId(), item.getInventoryId(), item.getQuantity()))
                .toList();
        return new SharedGroupPurchaseClosedEvent(
                purchase.getId(),
                purchase.getCreatorMemberId(),
                participantMemberIds,
                items,
                purchase.getClosedAt()
        );
    }
}
