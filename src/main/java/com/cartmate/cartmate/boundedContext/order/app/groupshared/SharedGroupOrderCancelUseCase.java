package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
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

    // 판매글 판매 중지: 결제 중·확정 공동 주문 전체 취소 → 기존 취소 이벤트로 배송 취소·전원 환불
    void cancelBySalesStopped(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelBySalesStopped(LocalDateTime.now()), order);
    }

    // 판매글 판매 중지: 확정 공동 주문에서 한 판매자 항목만 취소 → 그 배송 취소, 결제한 사람들에게 나눠 환불
    void cancelSellerItemsBySalesStopped(Long sharedGroupOrderId, Long sellerId, Long postId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        LocalDateTime now = LocalDateTime.now();
        long amount = order.cancelSellerItemsBySalesStopped(sellerId, now);
        if (amount > 0) {
            eventPublisher.publishEvent(new SharedGroupOrderSellerItemsCanceledEvent(
                   sharedGroupOrderId, sellerId, amount,
                    "HIDE-P%d-SG%d-S%d".formatted(postId, sharedGroupOrderId, sellerId), now));
        }
    }

    List<Long> findActiveIdsByProductIds(Collection<Long> productIds) {
        return sharedGroupOrderRepository.findIdsByStatusInAndProductIds(
                List.of(SharedGroupOrderStatus.PAYING, SharedGroupOrderStatus.CONFIRMED), productIds);
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
