package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryCancelUseCase {

    private final DeliveryRepository deliveryRepository;

    // 주문 취소에 맞춰 그 주문의 배송을 모두 취소한다. 하나라도 판매자가 확인한 뒤라면 예외로 전체가 취소되지 않는다.
    // 호출하는 쪽의 트랜잭션에 참여하므로 예외가 나면 주문 취소까지 함께 롤백된다.
    public void cancelByOrder(Long orderId) {
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(
                orderId, PurchaseType.INDIVIDUAL_GROUP);
        deliveries.forEach(Delivery::cancel);
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
