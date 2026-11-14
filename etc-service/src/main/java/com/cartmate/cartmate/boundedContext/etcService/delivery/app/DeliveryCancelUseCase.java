package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.BuyerCancelVerdict;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryOrderState;
import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.SalesStopCancelResult;
import com.cartmate.cartmate.shared.delivery.event.DeliverySalesStopCancelResultEvent;
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

    // 판매글 내림으로 인한 취소 요청을 판정하고 결과를 회신한다.
    // 공동구매는 모든 배송이 취소 가능하면 전부 취소하고, 아니면 그 판매자 몫만 취소한다. 그 판매자 배송이 발송됐으면 거절한다.
    // 배송이 아직 없는 판매자 몫은 메모를 남겨 나중에 온 배송 생성이 만들지 않게 한다.
    // 같은 요청을 다시 받아도 같은 결과를 돌려준다.
    public void cancelBySalesStop(PurchaseType purchaseType, Long orderId, Long sellerId) {
        DeliveryOrderState state = deliveryOrderStates.lockForSellerCancel(orderId, purchaseType).state();
        if (state.isCanceled()) {
            publishResult(purchaseType, orderId, sellerId, canceledResultOf(purchaseType), null);
            return;
        }

        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(orderId, purchaseType);
        LocalDateTime canceledAt = LocalDateTime.now();
        if (purchaseType == PurchaseType.SHARED_GROUP
                && deliveries.stream().allMatch(Delivery::isCancelableByPostHidden)) {
            cancelAll(deliveries, canceledAt);
            state.markCanceled();
            publishResult(purchaseType, orderId, sellerId, SalesStopCancelResult.WHOLE_CANCELED, null);
            return;
        }

        List<Delivery> sellerDeliveries = deliveries.stream()
                .filter(delivery -> delivery.getSellerId().equals(sellerId))
                .toList();
        if (!sellerDeliveries.stream().allMatch(Delivery::isCancelableByPostHidden)) {
            publishResult(purchaseType, orderId, sellerId, SalesStopCancelResult.REJECTED,
                    DeliveryErrorCode.DELIVERY_NOT_CANCELABLE.resultCode());
            return;
        }
        cancelAll(sellerDeliveries, canceledAt);
        deliveryOrderStates.markSellerCanceled(orderId, purchaseType, sellerId);
        publishResult(purchaseType, orderId, sellerId, SalesStopCancelResult.SELLER_CANCELED, null);
    }

    private SalesStopCancelResult canceledResultOf(PurchaseType purchaseType) {
        return purchaseType == PurchaseType.SHARED_GROUP
                ? SalesStopCancelResult.WHOLE_CANCELED
                : SalesStopCancelResult.SELLER_CANCELED;
    }

    private void cancelAll(List<Delivery> deliveries, LocalDateTime canceledAt) {
        deliveries.forEach(delivery -> delivery.cancelByPostHidden(canceledAt));
        deliveryRepository.saveAll(deliveries);
    }

    private void publishResult(PurchaseType purchaseType, Long orderId, Long sellerId,
                               SalesStopCancelResult result, String failureCode) {
        eventPublisher.publish(new DeliverySalesStopCancelResultEvent(purchaseType, orderId, sellerId, result, failureCode));
    }
}
