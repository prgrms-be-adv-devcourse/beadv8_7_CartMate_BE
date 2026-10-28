package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseItemQuantityResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SharedGroupPurchaseFacade {

    private final SharedGroupPurchaseCreateUseCase sharedGroupPurchaseCreateUseCase;
    private final SharedGroupPurchaseMemberUseCase sharedGroupPurchaseMemberUseCase;
    private final SharedGroupPurchaseItemUseCase sharedGroupPurchaseItemUseCase;
    private final SharedGroupPurchaseCloseUseCase sharedGroupPurchaseCloseUseCase;
    private final SharedGroupPurchaseQueryUseCase sharedGroupPurchaseQueryUseCase;

    @Transactional
    public SharedGroupPurchaseCreateResponse create(Long memberId, SharedGroupPurchaseCreateRequest request) {
        return sharedGroupPurchaseCreateUseCase.create(memberId, request);
    }

    @Transactional
    public SharedGroupPurchaseCreateResponse duplicate(Long memberId, Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseCreateUseCase.duplicate(memberId, sharedGroupPurchaseId);
    }

    @Transactional
    public Long join(Long memberId, String inviteCode) {
        return sharedGroupPurchaseMemberUseCase.join(memberId, inviteCode);
    }

    @Transactional
    public void leave(Long memberId, Long sharedGroupPurchaseId) {
        sharedGroupPurchaseMemberUseCase.leave(memberId, sharedGroupPurchaseId);
    }

    @Transactional
    public void kick(Long memberId, Long sharedGroupPurchaseId, Long targetMemberId) {
        sharedGroupPurchaseMemberUseCase.kick(memberId, sharedGroupPurchaseId, targetMemberId);
    }

    @Transactional
    public void increaseMaxParticipants(Long memberId, Long sharedGroupPurchaseId, int maxParticipants) {
        sharedGroupPurchaseMemberUseCase.increaseMaxParticipants(memberId, sharedGroupPurchaseId, maxParticipants);
    }

    @Transactional
    public void addItem(Long memberId, Long sharedGroupPurchaseId, Long inventoryId, int quantity) {
        sharedGroupPurchaseItemUseCase.addItem(memberId, sharedGroupPurchaseId, inventoryId, quantity);
    }

    @Transactional
    public void changeItemQuantity(Long memberId, Long sharedGroupPurchaseId, Long itemId, int quantity) {
        sharedGroupPurchaseItemUseCase.changeItemQuantity(memberId, sharedGroupPurchaseId, itemId, quantity);
    }

    @Transactional
    public SharedGroupPurchaseItemQuantityResponse adjustItemQuantity(Long memberId, Long sharedGroupPurchaseId, Long itemId, int delta) {
        return sharedGroupPurchaseItemUseCase.adjustItemQuantity(memberId, sharedGroupPurchaseId, itemId, delta);
    }

    @Transactional
    public void changeItemOption(Long memberId, Long sharedGroupPurchaseId, Long itemId, Long inventoryId) {
        sharedGroupPurchaseItemUseCase.changeItemOption(memberId, sharedGroupPurchaseId, itemId, inventoryId);
    }

    @Transactional
    public void removeItem(Long memberId, Long sharedGroupPurchaseId, Long itemId) {
        sharedGroupPurchaseItemUseCase.removeItem(memberId, sharedGroupPurchaseId, itemId);
    }

    @Transactional
    public void close(Long memberId, Long sharedGroupPurchaseId) {
        sharedGroupPurchaseCloseUseCase.close(memberId, sharedGroupPurchaseId);
    }

    @Transactional
    public void cancel(Long memberId, Long sharedGroupPurchaseId) {
        sharedGroupPurchaseCloseUseCase.cancel(memberId, sharedGroupPurchaseId);
    }

    @Transactional
    public void closeByDeadline(Long sharedGroupPurchaseId) {
        sharedGroupPurchaseCloseUseCase.closeByDeadline(sharedGroupPurchaseId);
    }

    @Transactional
    public void closeByFrozenTimeout(Long sharedGroupPurchaseId) {
        sharedGroupPurchaseCloseUseCase.closeByFrozenTimeout(sharedGroupPurchaseId);
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToCloseByDeadline(LocalDateTime now) {
        return sharedGroupPurchaseCloseUseCase.findIdsToCloseByDeadline(now);
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToReleaseFrozen(LocalDateTime now) {
        return sharedGroupPurchaseCloseUseCase.findIdsToReleaseFrozen(now);
    }

    @Transactional(readOnly = true)
    public SharedGroupPurchaseDetailResponse getDetail(Long memberId, Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseQueryUseCase.getDetail(memberId, sharedGroupPurchaseId);
    }

    @Transactional(readOnly = true)
    public List<SharedGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        return sharedGroupPurchaseQueryUseCase.getMyList(memberId);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findTitles(Collection<Long> sharedGroupPurchaseIds) {
        return sharedGroupPurchaseQueryUseCase.findTitles(sharedGroupPurchaseIds);
    }
}
