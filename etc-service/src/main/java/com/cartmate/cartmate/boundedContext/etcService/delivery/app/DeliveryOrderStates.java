package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryOrderState;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryOrderStatus;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryOrderStateRepository;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliverySellerCancelRepository;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DeliveryOrderStates {

    private final DeliveryOrderStateRepository deliveryOrderStateRepository;
    private final DeliverySellerCancelRepository deliverySellerCancelRepository;

    public boolean acceptCreation(Long orderId, PurchaseType purchaseType, Long sellerId) {
        if (lock(orderId, purchaseType, DeliveryOrderStatus.CREATED).state().isCanceled()) {
            return false;
        }
        return !deliverySellerCancelRepository.existsByOrderIdAndPurchaseTypeAndSellerId(orderId, purchaseType, sellerId);
    }

    public Locked lockForCancel(Long orderId, PurchaseType purchaseType) {
        return lock(orderId, purchaseType, DeliveryOrderStatus.CANCELED);
    }

    public Locked lockForSellerCancel(Long orderId, PurchaseType purchaseType) {
        return lock(orderId, purchaseType, DeliveryOrderStatus.CREATED);
    }

    public void markSellerCanceled(Long orderId, PurchaseType purchaseType, Long sellerId) {
        deliverySellerCancelRepository.insertIfAbsent(orderId, purchaseType.name(), sellerId, LocalDateTime.now());
    }

    private Locked lock(Long orderId, PurchaseType purchaseType, DeliveryOrderStatus initial) {
        boolean inserted = deliveryOrderStateRepository.insertIfAbsent(
                orderId, purchaseType.name(), initial.name(), LocalDateTime.now()) == 1;
        DeliveryOrderState state = deliveryOrderStateRepository.findForUpdate(orderId, purchaseType)
                .orElseThrow(() -> new IllegalStateException(
                        "주문 단위 배송 상태를 찾을 수 없다: orderId=" + orderId + ", purchaseType=" + purchaseType));
        return new Locked(state, inserted);
    }

    public record Locked(DeliveryOrderState state, boolean inserted) {
    }
}
