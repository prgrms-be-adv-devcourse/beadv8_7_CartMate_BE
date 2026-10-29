package com.cartmate.cartmate.boundedContext.cart.app;

import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

// 판매글이 내려가면 모집 중인 공동구매에서 그 상품을 뺀다. 남은 상품이 없으면 공동구매를 끝낸다.
@Service
@RequiredArgsConstructor
public class GroupPurchaseSalesStopUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Transactional(readOnly = true)
    public List<Long> findIndividualTargets(List<Long> productIds) {
        return productIds.isEmpty() ? List.of() : individualGroupPurchaseRepository.findOpenIdsByProductIds(productIds);
    }

    @Transactional(readOnly = true)
    public List<Long> findSharedTargets(List<Long> productIds) {
        return productIds.isEmpty() ? List.of() : sharedGroupPurchaseRepository.findEditableIdsByProductIds(productIds);
    }

    // 공동구매 하나마다 새 트랜잭션 (하나 실패해도 나머지 진행)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeFromIndividual(Long groupPurchaseId, List<Long> productIds) {
        individualGroupPurchaseRepository.findById(groupPurchaseId).ifPresent(purchase ->
                purchase.removeProductsBySalesStopped(new HashSet<>(productIds), LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeFromShared(Long groupPurchaseId, List<Long> productIds) {
        sharedGroupPurchaseRepository.findById(groupPurchaseId).ifPresent(purchase ->
                purchase.removeItemsBySalesStopped(new HashSet<>(productIds), LocalDateTime.now()));
    }
}