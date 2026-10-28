package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
