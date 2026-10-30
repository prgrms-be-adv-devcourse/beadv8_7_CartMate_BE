package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
class OrderCancelUseCase {

    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final ApplicationEventPublisher eventPublisher;

    void cancelByBuyer(Long memberId, Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        boolean paid = isPaid(order);
        publishCanceledEventIf(order.cancelByBuyer(memberId, LocalDateTime.now()), order, paid);
    }

    void cancelByDeadline(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        boolean paid = isPaid(order);
        publishCanceledEventIf(order.cancelByDeadline(LocalDateTime.now()), order, paid);
    }

    void cancelByOutOfStock(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        boolean paid = isPaid(order);
        publishCanceledEventIf(order.cancelByOutOfStock(LocalDateTime.now()), order, paid);
    }

    // 판매글 판매 중지: 결제 대기 주문 전체 취소
    void cancelBySalesStopped(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        publishCanceledEventIf(order.cancelBySalesStopped(LocalDateTime.now()), order, false);
    }

    // 판매글 판매 중지: 결제 완료 주문에서 한 판매자 항목만 취소 → delivery가 배송 취소, payment가 환불.
    // 주문이 전부 취소 상태가 되더라도 OrderCanceledEvent는 발행하지 않는다 (전액 환불이 겹쳐 이중 환불됨).
    void cancelSellerItemsBySalesStopped(Long orderId, Long sellerId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        LocalDateTime now = LocalDateTime.now();
        long amount = order.cancelSellerItemsBySalesStopped(sellerId, now);
        if (amount > 0) {
            eventPublisher.publishEvent(new OrderSellerItemsCanceledEvent(
                    orderId, order.getBuyerMemberId(), sellerId, amount, now));
        }
    }

    List<Long> findIdsByStatusAndProductIds(OrderStatus status, Collection<Long> productIds) {
        return orderRepository.findIdsByStatusAndProductIds(status, productIds);
    }

    List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return orderRepository.findIdsToCancelByDeadline(now);
    }

    private boolean isPaid(Order order) {
        return order.getStatus() == OrderStatus.PAID;
    }

    private void publishCanceledEventIf(boolean canceled, Order order, boolean paid) {
        if (canceled) {
            eventPublisher.publishEvent(toCanceledEvent(order, paid));
        }
    }

    private OrderCanceledEvent toCanceledEvent(Order order, boolean paid) {
        return new OrderCanceledEvent(
                order.getId(),
                order.getBuyerMemberId(),
                order.getOrderType().name(),
                order.getCancelReason().name(),
                paid,
                order.getCanceledAt()
        );
    }
}
