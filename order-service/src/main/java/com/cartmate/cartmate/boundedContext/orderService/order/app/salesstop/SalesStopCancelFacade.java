package com.cartmate.cartmate.boundedContext.orderService.order.app.salesstop;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop.SalesStopCancelRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop.SalesStopCancelRequestStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.salesstop.SalesStopCancelRequestRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliverySalesStopCancelResultEvent;
import com.cartmate.cartmate.shared.order.event.OrderSalesStopCancelRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 판매글 내림으로 인한 취소: 배송 서버에 먼저 취소를 요청하고, "취소됨"을 받은 뒤에만 항목을 취소하고 환불 이벤트를 낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesStopCancelFacade {

    private final SalesStopCancelRequestRepository requestRepository;
    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    // 판매글 숨김을 처리하는 주문마다 따로 커밋한다 (한 주문의 실패가 다른 주문의 요청을 되돌리지 않게).
    // 같은 판매자 몫은 한 번만 요청한다. 판매글을 다시 올렸다 내려도 새 요청을 만들지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void request(PurchaseType purchaseType, Long orderId, Long sellerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (requestRepository.insertIfAbsent(purchaseType.name(), orderId, sellerId, now) == 0) {
            log.info("이미 요청한 판매글 내림 취소: purchaseType={}, orderId={}, sellerId={}", purchaseType, orderId, sellerId);
            return;
        }
        eventPublisher.publish(new OrderSalesStopCancelRequestedEvent(purchaseType, orderId, sellerId, now));
    }

    @Transactional
    public void applyResult(DeliverySalesStopCancelResultEvent event) {
        Optional<SalesStopCancelRequest> found = requestRepository.findForUpdate(
                event.purchaseType(), event.orderId(), event.sellerId());
        if (found.isEmpty() || !found.get().isRequested()) {
            log.info("반영하지 않는 판매글 내림 취소 결과: purchaseType={}, orderId={}, sellerId={}, result={}, status={}",
                    event.purchaseType(), event.orderId(), event.sellerId(), event.result(),
                    found.map(SalesStopCancelRequest::getStatus).orElse(null));
            return;
        }
        SalesStopCancelRequest request = found.get();
        switch (event.result()) {
            case REJECTED -> request.reject(event.failureCode());
            case WHOLE_CANCELED -> {
                cancelWhole(event);
                request.complete();
            }
            case SELLER_CANCELED -> {
                cancelSeller(event);
                request.complete();
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToResend(LocalDateTime lastSentBefore) {
        return requestRepository.findIdsByStatusAndLastSentAtBefore(SalesStopCancelRequestStatus.REQUESTED, lastSentBefore);
    }

    @Transactional
    public void resend(Long requestId, Duration resendAfter) {
        requestRepository.findByIdForUpdate(requestId)
                .filter(request -> request.resend(LocalDateTime.now(clock), resendAfter))
                .ifPresent(request -> {
                    log.warn("배송 서버 응답이 없어 판매글 내림 취소 요청 재발행: purchaseType={}, orderId={}, sellerId={}, firstRequestedAt={}",
                            request.getPurchaseType(), request.getOrderId(), request.getSellerId(), request.getRequestedAt());
                    eventPublisher.publish(new OrderSalesStopCancelRequestedEvent(
                            request.getPurchaseType(), request.getOrderId(), request.getSellerId(), request.getRequestedAt()));
                });
    }

    // 개인 주문에는 전체 취소 판정이 없다. 혹시 오면 그 판매자 몫 취소로 처리한다.
    private void cancelWhole(DeliverySalesStopCancelResultEvent event) {
        if (event.purchaseType() == PurchaseType.SHARED_GROUP) {
            sharedGroupOrderFacade.cancelWholeBySalesStopResult(event.orderId());
            return;
        }
        cancelSeller(event);
    }

    private void cancelSeller(DeliverySalesStopCancelResultEvent event) {
        if (event.purchaseType() == PurchaseType.SHARED_GROUP) {
            sharedGroupOrderFacade.cancelSellerItems(event.orderId(), event.sellerId(), DeliveryCancelReason.SALES_STOPPED);
            return;
        }
        orderFacade.cancelSellerItems(event.orderId(), event.sellerId(), DeliveryCancelReason.SALES_STOPPED);
    }
}
