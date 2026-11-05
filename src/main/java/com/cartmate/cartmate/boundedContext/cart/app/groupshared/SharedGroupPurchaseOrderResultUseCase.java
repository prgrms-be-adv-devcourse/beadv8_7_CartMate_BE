package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreationFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 공동 주문 생성 결과(order 이벤트)를 공동구매에 반영한다. 생성자의 재마감 · 취소와 순서를 맞추려고 행 락을 건다.
// 결과가 제한 시간 안에 오지 않으면(consumer가 계속 실패 · 이벤트 유실) 스케줄러가 FROZEN으로 돌린다
@Slf4j
@Service
class SharedGroupPurchaseOrderResultUseCase {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final Duration orderCreationTimeout;

    SharedGroupPurchaseOrderResultUseCase(
            SharedGroupPurchaseRepository sharedGroupPurchaseRepository,
            @Value("${custom.shared-group-purchase.order-creation-timeout:PT10M}") Duration orderCreationTimeout) {
        this.sharedGroupPurchaseRepository = sharedGroupPurchaseRepository;
        this.orderCreationTimeout = orderCreationTimeout;
    }

    void markOrderCreated(SharedGroupOrderCreatedEvent event) {
        findForUpdate(event.sharedGroupPurchaseId()).ifPresent(purchase -> {
            if (!purchase.markOrderCreated() && !purchase.isOrderCreated()) {
                // 생성 대기가 아닌데 주문이 생겼다(예: 시간 초과로 FROZEN에 돌아간 뒤 취소). 주문 쪽 정리가 필요할 수 있다
                log.warn("공동 주문이 생겼지만 공동구매가 받을 수 없는 상태: sharedGroupPurchaseId={} status={} sharedGroupOrderId={}",
                        purchase.getId(), purchase.getStatus(), event.sharedGroupOrderId());
            }
        });
    }

    void revertByOrderCreationFailed(SharedGroupOrderCreationFailedEvent event) {
        findForUpdate(event.sharedGroupPurchaseId()).ifPresent(purchase -> {
            if (purchase.revertToFrozen(LocalDateTime.now())) {
                log.warn("공동 주문 생성 실패로 마감 보류로 돌림: sharedGroupPurchaseId={} reason={}",
                        purchase.getId(), event.reason());
            }
        });
    }

    List<Long> findIdsAwaitingOrderTooLong(LocalDateTime now) {
        return sharedGroupPurchaseRepository.findIdsAwaitingOrderClosedBefore(now.minus(orderCreationTimeout));
    }

    // 조회와 처리 사이에 생성 소식이 왔을 수 있어 락을 잡고 다시 본다
    void revertByOrderCreationTimeout(Long sharedGroupPurchaseId, LocalDateTime now) {
        findForUpdate(sharedGroupPurchaseId).ifPresent(purchase -> {
            if (purchase.isOrderCreationOverdue(orderCreationTimeout, now) && purchase.revertToFrozen(now)) {
                log.warn("공동 주문 생성이 {} 안에 끝나지 않아 마감 보류로 돌림: sharedGroupPurchaseId={} closedAt={}",
                        orderCreationTimeout, purchase.getId(), purchase.getClosedAt());
            }
        });
    }

    // 없는 공동구매는 다시 해도 없으니 예외로 재시도시키지 않고 넘긴다
    private Optional<SharedGroupPurchase> findForUpdate(Long sharedGroupPurchaseId) {
        Optional<SharedGroupPurchase> purchase = sharedGroupPurchaseRepository.findByIdForUpdate(sharedGroupPurchaseId);
        if (purchase.isEmpty()) {
            log.warn("공동 주문 생성 결과를 받았지만 공동구매가 없음: sharedGroupPurchaseId={}", sharedGroupPurchaseId);
        }
        return purchase;
    }
}
