package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreationFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
class SharedGroupPurchaseOrderResultUseCase {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final Duration orderCreationTimeout;
    private final Clock clock;

    SharedGroupPurchaseOrderResultUseCase(
            SharedGroupPurchaseRepository sharedGroupPurchaseRepository,
            @Value("${custom.shared-group-purchase.order-creation-timeout:PT10M}") Duration orderCreationTimeout,
            Clock clock) {
        this.sharedGroupPurchaseRepository = sharedGroupPurchaseRepository;
        this.orderCreationTimeout = orderCreationTimeout;
        this.clock = clock;
    }

    void markOrderCreated(SharedGroupOrderCreatedEvent event) {
        findForUpdate(event.sharedGroupPurchaseId()).ifPresent(purchase -> {
            if (!purchase.markOrderCreated() && !purchase.isOrderCreated()) {
                log.warn("공동 주문이 생겼지만 공동구매가 받을 수 없는 상태: sharedGroupPurchaseId={} status={} sharedGroupOrderId={}",
                        purchase.getId(), purchase.getStatus(), event.sharedGroupOrderId());
            }
        });
    }

    void revertByOrderCreationFailed(SharedGroupOrderCreationFailedEvent event) {
        findForUpdate(event.sharedGroupPurchaseId()).ifPresent(purchase -> {
            if (purchase.revertToFrozen(LocalDateTime.now(clock))) {
                log.warn("공동 주문 생성 실패로 마감 보류로 돌림: sharedGroupPurchaseId={} reason={}",
                        purchase.getId(), event.reason());
            }
        });
    }

    List<Long> findIdsAwaitingOrderTooLong(LocalDateTime now) {
        return sharedGroupPurchaseRepository.findIdsAwaitingOrderClosedBefore(now.minus(orderCreationTimeout));
    }

    void revertByOrderCreationTimeout(Long sharedGroupPurchaseId, LocalDateTime now) {
        findForUpdate(sharedGroupPurchaseId).ifPresent(purchase -> {
            if (purchase.isOrderCreationOverdue(orderCreationTimeout, now) && purchase.revertToFrozen(now)) {
                log.warn("공동 주문 생성이 {} 안에 끝나지 않아 마감 보류로 돌림: sharedGroupPurchaseId={} closedAt={}",
                        orderCreationTimeout, purchase.getId(), purchase.getClosedAt());
            }
        });
    }

    private Optional<SharedGroupPurchase> findForUpdate(Long sharedGroupPurchaseId) {
        Optional<SharedGroupPurchase> purchase = sharedGroupPurchaseRepository.findByIdForUpdate(sharedGroupPurchaseId);
        if (purchase.isEmpty()) {
            log.warn("공동 주문 생성 결과를 받았지만 공동구매가 없음: sharedGroupPurchaseId={}", sharedGroupPurchaseId);
        }
        return purchase;
    }
}
