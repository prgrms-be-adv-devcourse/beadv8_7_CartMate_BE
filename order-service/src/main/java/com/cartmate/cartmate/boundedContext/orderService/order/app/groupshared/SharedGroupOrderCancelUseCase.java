package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
class SharedGroupOrderCancelUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final SharedGroupOrderSupport sharedGroupOrderSupport;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    void cancelByCreator(Long memberId, Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelByCreator(memberId, LocalDateTime.now(clock)), order);
    }

    void cancelByDeadline(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelByDeadline(LocalDateTime.now(clock)), order);
    }

    void cancelByOutOfStock(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelByOutOfStock(LocalDateTime.now(clock)), order);
    }

    // 판매글 판매 중지: 결제 중·확정 공동 주문 전체 취소 → 기존 취소 이벤트로 배송 취소·전원 환불
    void cancelBySalesStopped(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelBySalesStopped(LocalDateTime.now(clock)), order);
    }

    // 판매글 판매 중지: 확정 공동 주문에서 한 판매자 항목만 취소 → 그 배송 취소, 결제한 사람들에게 나눠 환불
    void cancelSellerItemsBySalesStopped(Long sharedGroupOrderId, Long sellerId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        LocalDateTime now = LocalDateTime.now(clock);
        long amount = order.cancelSellerItemsBySalesStopped(sellerId, now);
        if (amount > 0) {
            eventPublisher.publish(new SharedGroupOrderSellerItemsCanceledEvent(
                    sharedGroupOrderId, sellerId, amount, now));
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
            eventPublisher.publish(toCanceledEvent(order));
        }
    }

    private SharedGroupOrderCanceledEvent toCanceledEvent(SharedGroupOrder order) {
        return new SharedGroupOrderCanceledEvent(
                order.getId(),
                order.getSharedGroupPurchaseId(),
                order.getCancelReason(),
                order.getCanceledAt(),
                order.getUnpaidMemberIds(),
                order.getCreatorMemberId()
        );
    }
}
