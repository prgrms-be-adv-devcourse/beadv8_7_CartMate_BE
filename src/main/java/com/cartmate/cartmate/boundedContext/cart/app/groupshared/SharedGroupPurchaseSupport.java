package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseItem;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseSupport {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final ProductClient productClient;

    SharedGroupPurchase save(SharedGroupPurchase purchase) {
        return sharedGroupPurchaseRepository.save(purchase);
    }

    boolean existsByInviteCode(String inviteCode) {
        return sharedGroupPurchaseRepository.existsByInviteCode(inviteCode);
    }

    SharedGroupPurchase getPurchase(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseRepository.findById(sharedGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
    }

    SharedGroupPurchase getPurchaseForUpdate(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseRepository.findByIdForUpdate(sharedGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
    }

    SharedGroupPurchase getPurchaseByInviteCodeForUpdate(String inviteCode) {
        return sharedGroupPurchaseRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));
    }

    Optional<SharedGroupPurchase> findWithDetails(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseRepository.findWithDetailsById(sharedGroupPurchaseId);
    }

    List<SharedGroupPurchase> findAllByParticipant(Long memberId) {
        return sharedGroupPurchaseRepository.findAllByParticipant(memberId);
    }

    List<Long> findIdsToCloseByDeadline(LocalDateTime now) {
        return sharedGroupPurchaseRepository.findIdsToCloseByDeadline(now);
    }

    List<Long> findIdsToReleaseFrozen(LocalDateTime now) {
        return sharedGroupPurchaseRepository.findIdsToReleaseFrozen(now);
    }

    Set<Long> findUnavailableInventoryIds(SharedGroupPurchase purchase) {
        return productClient.findUnavailableInventoryIds(inventoryIdsOf(purchase));
    }

    List<Long> inventoryIdsOf(SharedGroupPurchase purchase) {
        return purchase.getItems().stream()
                .map(SharedGroupPurchaseItem::getInventoryId)
                .toList();
    }
}
