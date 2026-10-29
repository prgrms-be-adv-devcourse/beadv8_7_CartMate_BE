package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class SharedGroupOrderCancelUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final SharedGroupOrderSupport sharedGroupOrderSupport;
    private final ApplicationEventPublisher eventPublisher;

    void cancelByCreator(Long memberId, Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelByCreator(memberId, LocalDateTime.now()), order);
    }

    void cancelByDeadline(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelByDeadline(LocalDateTime.now()), order);
    }

    void cancelByOutOfStock(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelByOutOfStock(LocalDateTime.now()), order);
    }

    List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return sharedGroupOrderRepository.findIdsToCancelByDeadline(now);
    }

    private void publishCanceledEventIf(boolean canceled, SharedGroupOrder order) {
        if (canceled) {
            eventPublisher.publishEvent(toCanceledEvent(order));
        }
    }

    private SharedGroupOrderCanceledEvent toCanceledEvent(SharedGroupOrder order) {
        return new SharedGroupOrderCanceledEvent(
                order.getId(),
                order.getSharedGroupPurchaseId(),
                order.getCancelReason().name(),
                order.getCanceledAt(),
                order.getUnpaidMemberIds()
        );
    }
}
