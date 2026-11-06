package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import lombok.extern.slf4j.Slf4j;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;

import com.cartmate.cartmate.global.exception.ServiceException;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;

import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
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
    private final OrderPaymentClient orderPaymentClient;
    private final Clock clock;

    void cancelByBuyer(Long memberId, Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        boolean paid = isPaid(order);
        publishCanceledEventIf(order.cancelByBuyer(memberId, LocalDateTime.now(clock)), order, paid);
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

    // 판매글 판매 중지: 결제 대기 주문 전체 취소
    void cancelBySalesStopped(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        publishCanceledEventIf(order.cancelBySalesStopped(LocalDateTime.now(clock)), order, false);
    }

    // 개인물품 공동구매가 닫힘: 결제 대기 주문 취소 (결제 전이라 환불 · 배송 없음, OrderCanceledEvent는 나간다)
    void cancelByGroupPurchaseClosed(Long orderId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        publishCanceledEventIf(order.cancelByGroupPurchaseClosed(LocalDateTime.now(clock)), order, false);
    }

    List<Long> findPendingIdsOfIndividualGroupPurchase(Long individualGroupPurchaseId) {
        return orderRepository.findIdsByIndividualGroupPurchaseIdAndStatus(individualGroupPurchaseId,
                OrderStatus.PAYMENT_PENDING);
    }

    // 판매글 판매 중지: 결제 완료 주문에서 한 판매자 항목만 취소 → delivery가 배송 취소, payment가 환불.
    // 주문이 전부 취소 상태가 되더라도 OrderCanceledEvent는 발행하지 않는다 (전액 환불이 겹쳐 이중 환불됨).
    void cancelSellerItemsBySalesStopped(Long orderId, Long sellerId) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        LocalDateTime now = LocalDateTime.now(clock);
        long amount = order.cancelSellerItemsBySalesStopped(sellerId, now);
        if (amount > 0) {
            eventPublisher.publish(new OrderSellerItemsCanceledEvent(
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
        if (!canceled) {
            return;
        }
        if (!paid) {
            releaseUnappliedCharge(order);
        }
        eventPublisher.publish(toCanceledEvent(order, paid));
    }

    /**
     * 결제 대기 주문을 취소할 때, 결제 서버에서 차감은 됐지만 주문에 반영되지 못한 결제가 남아 있으면 되돌린다(ISS-27).
     * 결제 반영 중 다른 서버가 잠깐 503이면 차감을 되돌리지 않고 재시도에 맡기는데, 구매자가 다시 결제하지 않고 주문이
     * 취소되면(기한 만료 · 구매자 · 판매 중지 · 공동구매 종료) 취소 이벤트는 결제 전({@code paid=false})으로 나가
     * payment가 환불하지 않는다. 그래서 여기서 결제 서버에 묻고 되돌린다.
     *
     * <p>되돌리기는 실패해도 예외를 던지지 않으므로(포트 약속) 다시 조회해 확인한다. 여전히 결제돼 있거나 결제 서버를
     * 조회할 수 없으면 예외로 이 취소를 롤백한다 — 돈이 빠진 채 취소되는 것보다 취소가 늦어지는 편이 낫다.
     * 기한 만료 스케줄러 · 이벤트 소비자는 다음에 다시 시도하고, 구매자 취소는 잠시 뒤 다시 하면 된다.
     * 되돌리기는 결제 서버에서 같은 결제에 한 번만 일어난다(환불 키).
     */
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
