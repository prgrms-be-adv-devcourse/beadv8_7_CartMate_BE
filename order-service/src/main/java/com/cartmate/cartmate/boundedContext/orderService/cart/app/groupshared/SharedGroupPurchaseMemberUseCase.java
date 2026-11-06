package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberJoinedEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseMemberUseCase {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    Long join(Long memberId, String inviteCode) {
        SharedGroupPurchase purchase = getPurchaseByInviteCodeForUpdate(inviteCode);
        if (purchase.isMember(memberId)) {
            return purchase.getId();
        }
        sharedGroupPurchaseSupport.validateNotRestricted(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        purchase.join(memberId, now);
        long activeParticipationCount = sharedGroupPurchaseRepository.countActiveOfMember(memberId);
        eventPublisher.publish(new SharedGroupPurchaseMemberJoinedEvent(
                purchase.getId(), memberId, activeParticipationCount, now));
        return purchase.getId();
    }

    void leave(Long memberId, Long sharedGroupPurchaseId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime joinedAt = sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .leave(memberId, now);
        eventPublisher.publish(new SharedGroupPurchaseMemberLeftEvent(sharedGroupPurchaseId, memberId, joinedAt, now));
    }

    void kick(Long memberId, Long sharedGroupPurchaseId, Long targetMemberId) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .kick(memberId, targetMemberId, LocalDateTime.now(clock));
    }

    void increaseMaxParticipants(Long memberId, Long sharedGroupPurchaseId, int maxParticipants) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .increaseMaxParticipants(memberId, maxParticipants, LocalDateTime.now(clock));
    }

    private SharedGroupPurchase getPurchaseByInviteCodeForUpdate(String inviteCode) {
        return sharedGroupPurchaseRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_INVALID_INVITE_CODE));
    }
}
