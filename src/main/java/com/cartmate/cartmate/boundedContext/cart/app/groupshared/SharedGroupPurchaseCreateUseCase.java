package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.app.InviteCodeGenerator;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseCreateUseCase {

    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final InviteCodeGenerator inviteCodeGenerator;

    SharedGroupPurchaseCreateResponse create(Long memberId, SharedGroupPurchaseCreateRequest request) {
        String inviteCode = generateInviteCode();
        SharedGroupPurchase purchase = SharedGroupPurchase.create(
                request.title(), memberId, request.maxParticipants(), inviteCode, LocalDateTime.now());
        SharedGroupPurchase saved = sharedGroupPurchaseSupport.save(purchase);
        return SharedGroupPurchaseCreateResponse.from(saved);
    }

    SharedGroupPurchaseCreateResponse duplicate(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase source = sharedGroupPurchaseSupport.getPurchase(sharedGroupPurchaseId);
        Set<Long> unavailable = sharedGroupPurchaseSupport.findUnavailableInventoryIds(source);
        String inviteCode = generateInviteCode();
        SharedGroupPurchase copy = source.duplicate(memberId, inviteCode, unavailable, LocalDateTime.now());
        SharedGroupPurchase saved = sharedGroupPurchaseSupport.save(copy);
        return SharedGroupPurchaseCreateResponse.duplicated(saved, unavailable);
    }

    private String generateInviteCode() {
        return inviteCodeGenerator.generateUnique(sharedGroupPurchaseSupport::existsByInviteCode);
    }
}
