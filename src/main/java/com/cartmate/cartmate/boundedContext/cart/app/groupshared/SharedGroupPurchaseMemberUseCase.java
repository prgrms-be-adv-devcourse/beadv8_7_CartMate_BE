package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseMemberUseCase {

    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;

    Long join(Long memberId, String inviteCode) {
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.getPurchaseByInviteCodeForUpdate(inviteCode);
        if (purchase.isMember(memberId)) {
            return purchase.getId();
        }
        purchase.join(memberId, LocalDateTime.now());
        return purchase.getId();
    }

    void leave(Long memberId, Long sharedGroupPurchaseId) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .leave(memberId, LocalDateTime.now());
    }

    void kick(Long memberId, Long sharedGroupPurchaseId, Long targetMemberId) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .kick(memberId, targetMemberId, LocalDateTime.now());
    }

    void increaseMaxParticipants(Long memberId, Long sharedGroupPurchaseId, int maxParticipants) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .increaseMaxParticipants(memberId, maxParticipants, LocalDateTime.now());
    }
}
