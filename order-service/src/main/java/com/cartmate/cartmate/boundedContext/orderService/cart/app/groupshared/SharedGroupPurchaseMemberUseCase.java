package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseMemberUseCase {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final Clock clock;

    Long join(Long memberId, String inviteCode) {
        SharedGroupPurchase purchase = getPurchaseByInviteCodeForUpdate(inviteCode);
        if (purchase.isMember(memberId)) {
            return purchase.getId();
        }
        sharedGroupPurchaseSupport.validateNotRestricted(memberId);
        purchase.join(memberId, LocalDateTime.now(clock));
        return purchase.getId();
    }

    void leave(Long memberId, Long sharedGroupPurchaseId) {
        sharedGroupPurchaseSupport.getPurchaseForUpdate(sharedGroupPurchaseId)
                .leave(memberId, LocalDateTime.now(clock));
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
