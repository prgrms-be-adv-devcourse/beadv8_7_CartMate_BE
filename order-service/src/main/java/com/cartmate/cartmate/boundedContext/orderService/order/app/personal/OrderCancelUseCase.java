package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient;
import lombok.extern.slf4j.Slf4j;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;

import com.cartmate.cartmate.global.exception.ServiceException;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;

import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.BuyerCancelResult;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.shared.order.event.OrderCancelRequestedEvent;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class OrderCancelUseCase {

    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final DomainEventPublisher eventPublisher;
    private final OrderCouponClient orderCouponClient;
    private final OrderPaymentClient orderPaymentClient;
    private final Clock clock;

    BuyerCancelResult cancelByBuyer(Long memberId, Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        BuyerCancelResult result = order.cancelByBuyer(memberId, LocalDateTime.now(clock));
        if (result == BuyerCancelResult.CANCELED) {
            publishCanceledEventIf(true, order, false);
            restoreCouponIf(true, order);
        } else {
            publishCancelRequested(order);
        }
        return result;
    }

    void cancelByDeadline(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        boolean paid = isPaid(order);
        publishCanceledEventIf(order.cancelByDeadline(LocalDateTime.now(clock)), order, paid);
    }

    void cancelByOutOfStock(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        boolean paid = isPaid(order);
        publishCanceledEventIf(order.cancelByOutOfStock(LocalDateTime.now(clock)), order, paid);
    }

    void cancelBySalesStopped(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        publishCanceledEventIf(order.cancelBySalesStopped(LocalDateTime.now(clock)), order, false);
    }

    void cancelByGroupPurchaseClosed(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        publishCanceledEventIf(order.cancelByGroupPurchaseClosed(LocalDateTime.now(clock)), order, false);
    }

    List<Long> findPendingIdsOfIndividualGroupPurchase(Long individualGroupPurchaseId) {
        return orderRepository.findIdsByIndividualGroupPurchaseIdAndStatus(individualGroupPurchaseId,
                OrderStatus.PAYMENT_PENDING);
    }

    void cancelSellerItemsBySalesStopped(Long orderId, Long sellerId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        LocalDateTime now = LocalDateTime.now(clock);
        long amount = order.cancelSellerItemsBySalesStopped(sellerId, now);
        if (amount > 0) {
            eventPublisher.publish(new OrderSellerItemsCanceledEvent(
                    orderId, order.getBuyerMemberId(), sellerId, amount, now));
        }
        restoreCouponIf(amount > 0, order);
    }

    List<Long> findIdsByStatusAndProductIds(OrderStatus status, Collection<Long> productIds) {
        return orderRepository.findIdsByStatusAndProductIds(status, productIds);
    }

    List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return orderRepository.findIdsToCancelByDeadline(now);
    }

    private void restoreCouponIf(boolean canceled, Order order) {
        if (canceled && order.couponRestorable()) {
            orderCouponClient.restore(order.getBuyerMemberId(), order.getMemberCouponId(), order.getId());
        }
    }

    private boolean isPaid(Order order) {
        return order.isPaid();
    }

    private void publishCancelRequested(Order order) {
        eventPublisher.publish(new OrderCancelRequestedEvent(
                order.getId(), order.getBuyerMemberId(), order.getCancelRequestSeq(), order.getCancelRequestedAt()));
    }

    private void publishCanceledEventIf(boolean canceled, Order order, boolean paid) {
        if (!canceled) {
            return;
        }
        if (!paid) {
            releaseUnappliedCharge(order);
        }
        eventPublisher.publish(toCanceledEvent(order, paid));
    }

    private void releaseUnappliedCharge(Order order) {
        Long orderId = order.getId();
        Long buyer = order.getBuyerMemberId();
        if (orderPaymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, orderId, buyer)
                .filter(PaymentResult::isPaid).isEmpty()) {
            return;
        }
        log.warn("결제 대기 주문 취소 중 반영 안 된 결제 발견, 되돌림: orderId={} buyer={}", orderId, buyer);
        orderPaymentClient.compensateCharge(PaymentOrderType.INDIVIDUAL_GROUP, orderId, buyer);
        if (orderPaymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, orderId, buyer)
                .filter(PaymentResult::isPaid).isPresent()) {
            throw new ServiceException(OrderErrorCode.ORDER_PAYMENT_RELEASE_PENDING);
        }
    }

    private OrderCanceledEvent toCanceledEvent(Order order, boolean paid) {
        return new OrderCanceledEvent(
                order.getId(),
                order.getBuyerMemberId(),
                order.getOrderType(),
                order.getCancelReason(),
                paid,
                order.getCanceledAt()
        );
    }
}
