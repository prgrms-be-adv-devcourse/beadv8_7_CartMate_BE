package com.cartmate.cartmate.boundedContext.orderService.cart.app;

import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseEvents;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
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
    private final DomainEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Long> findIndividualTargets(List<Long> productIds) {
        return productIds.isEmpty() ? List.of() : individualGroupPurchaseRepository.findOpenIdsByProductIds(productIds);
    }

    @Transactional(readOnly = true)
    public List<Long> findSharedTargets(List<Long> productIds) {
        return productIds.isEmpty() ? List.of() : sharedGroupPurchaseRepository.findEditableIdsByProductIds(productIds);
    }

    // 공동구매 하나마다 새 트랜잭션 (하나 실패해도 나머지 진행).
    // 개설자 · 어드민 마감, 시스템 마감과 같은 행을 바꾸므로 같은 행 락을 잡아 서로 덮어쓰거나 종료 이벤트를 두 번 내지 않게 한다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeFromIndividual(Long groupPurchaseId, List<Long> productIds) {
        individualGroupPurchaseRepository.findByIdForUpdate(groupPurchaseId).ifPresent(purchase -> {
            boolean changed = purchase.removeProductsBySalesStopped(new HashSet<>(productIds), LocalDateTime.now());
            if (!changed) {
                return;
            }
            eventPublisher.publish(purchase.isClosed()
                    ? IndividualGroupPurchaseEvents.closed(purchase, IndividualGroupPurchaseCloseReason.SALES_STOPPED)
                    : IndividualGroupPurchaseEvents.updated(purchase, LocalDateTime.now()));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeFromShared(Long groupPurchaseId, List<Long> productIds) {
        sharedGroupPurchaseRepository.findById(groupPurchaseId).ifPresent(purchase ->
                purchase.removeItemsBySalesStopped(new HashSet<>(productIds), LocalDateTime.now()));
    }
}