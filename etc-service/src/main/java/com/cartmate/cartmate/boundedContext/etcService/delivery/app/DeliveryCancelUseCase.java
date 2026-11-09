package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.BuyerCancelVerdict;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryOrderState;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCancelRejectedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryCancelUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryOrderStates deliveryOrderStates;
    private final DomainEventPublisher eventPublisher;

    public void cancelByBuyerRequest(Long orderId, int requestSeq, LocalDateTime requestedAt) {
        DeliveryOrderStates.Locked locked = deliveryOrderStates.lockForCancel(orderId, PurchaseType.INDIVIDUAL_GROUP);
        DeliveryOrderState state = locked.state();
        if (state.isCanceled() && !locked.inserted()) {
            eventPublisher.publish(new DeliveryOrderCanceledEvent(orderId, requestSeq));
            return;
        }

        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(
                orderId, PurchaseType.INDIVIDUAL_GROUP);
        Optional<BuyerCancelVerdict> rejection = rejectionOf(deliveries, requestedAt);
        if (rejection.isPresent()) {
            state.markCreated();
            eventPublisher.publish(new DeliveryOrderCancelRejectedEvent(
                    orderId, requestSeq, rejection.get().getRejectCode().resultCode()));
            return;
        }

        LocalDateTime canceledAt = LocalDateTime.now();
        deliveries.forEach(delivery -> delivery.cancelByBuyerRequest(requestedAt, canceledAt));
        deliveryRepository.saveAll(deliveries);
        state.markCanceled();
        eventPublisher.publish(new DeliveryOrderCanceledEvent(orderId, requestSeq));
    }

    private Optional<BuyerCancelVerdict> rejectionOf(List<Delivery> deliveries, LocalDateTime requestedAt) {
        List<BuyerCancelVerdict> verdicts = deliveries.stream()
                .map(delivery -> delivery.buyerCancelVerdict(requestedAt))
                .toList();
        if (verdicts.contains(BuyerCancelVerdict.NOT_CANCELABLE)) {
            return Optional.of(BuyerCancelVerdict.NOT_CANCELABLE);
        }
        if (verdicts.contains(BuyerCancelVerdict.SHIPPED_AFTER_REQUEST)) {
            return Optional.of(BuyerCancelVerdict.SHIPPED_AFTER_REQUEST);
        }
        return Optional.empty();
    }

    // 판매글 판매 중지: 한 주문에서 한 판매자 배송만 취소 (발송됐으면 예외).
    // 변경 감지에 기대지 않고 저장을 명시해, 어떤 배송이 바뀌는지 코드에서 드러나게 한다.
    public void cancelSellerBySalesStop(Long orderId, PurchaseType purchaseType, Long sellerId, LocalDateTime canceledAt) {
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(orderId, purchaseType).stream()
                .filter(delivery -> delivery.getSellerId().equals(sellerId))
                .toList();
                deliveries.forEach(delivery -> delivery.cancelByPostHidden(canceledAt));
                deliveryRepository.saveAll(deliveries);
    }

    // 판매글 판매 중지: 공동 주문 배송 전부 취소. 하나라도 발송됐으면 예외 → 공동 주문 취소까지 롤백.
    // 결제 중이던 공동 주문은 아직 배송이 없어 아무 일도 없다.
    public void cancelAllSharedBySalesStop(Long sharedGroupOrderId, LocalDateTime canceledAt) {
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(
                sharedGroupOrderId, PurchaseType.SHARED_GROUP);
        deliveries.forEach(delivery -> delivery.cancelByPostHidden(canceledAt));
        deliveryRepository.saveAll(deliveries);
    }
}
