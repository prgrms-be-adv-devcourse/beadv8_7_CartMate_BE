package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent.SellerAmount;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    void cancelBySalesStopped(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        publishCanceledEventIf(order.cancelBySalesStopped(LocalDateTime.now(clock)), order);
    }

    void cancelSellerItems(Long sharedGroupOrderId, Long sellerId, DeliveryCancelReason reason) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, Long> canceledBefore = order.canceledAmountsBySeller();
        long amount = order.cancelSellerItems(sellerId, reason, now);
        if (amount == 0) {
            return;
        }
        // 마지막 판매자 몫까지 취소되면 전체 취소로 환불한다.
        // 판매자 몫마다 따로 나누면 1원 끝전이 한 사람에게 쌓여, 낸 돈보다 많이 돌려주려다 실패할 수 있다.
        if (order.getStatus() == SharedGroupOrderStatus.CANCELED) {
            eventPublisher.publish(toCanceledEvent(order, canceledBefore));
            return;
        }
        eventPublisher.publish(new SharedGroupOrderSellerItemsCanceledEvent(
                sharedGroupOrderId, sellerId, amount, now, reason));
    }

    List<Long> findIdsByStatusAndProductIds(SharedGroupOrderStatus status, Collection<Long> productIds) {
        return sharedGroupOrderRepository.findIdsByStatusInAndProductIds(List.of(status), productIds);
    }

    List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return sharedGroupOrderRepository.findIdsToCancelByDeadline(now);
    }

    private void publishCanceledEventIf(boolean canceled, SharedGroupOrder order) {
        if (canceled) {
            eventPublisher.publish(toCanceledEvent(order, order.canceledAmountsBySeller()));
        }
    }

    // 먼저 판매자 몫으로 환불한 금액을 결제 서버가 전체 취소 환불에서 빼도록 함께 보낸다
    private SharedGroupOrderCanceledEvent toCanceledEvent(SharedGroupOrder order, Map<Long, Long> canceledBefore) {
        List<SellerAmount> canceledSellerAmounts = canceledBefore.entrySet().stream()
                .map(entry -> new SellerAmount(entry.getKey(), entry.getValue()))
                .toList();
        return new SharedGroupOrderCanceledEvent(
                order.getId(),
                order.getSharedGroupPurchaseId(),
                order.getCancelReason(),
                order.getCanceledAt(),
                order.getUnpaidMemberIds(),
                order.getCreatorMemberId(),
                canceledSellerAmounts
        );
    }
}
