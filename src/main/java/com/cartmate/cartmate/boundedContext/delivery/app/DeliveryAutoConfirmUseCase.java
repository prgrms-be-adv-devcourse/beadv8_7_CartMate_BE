package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryAutoConfirmUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryConfirmedEventPublisher eventPublisher;

    // 배송완료된 배송을 구매확정으로 바꾼다. 시스템이 호출하는 처리라 회원 권한은 확인하지 않는다.
    // 조회 이후 구매자가 먼저 확정했으면 이미 구매확정이라 아무것도 하지 않고 이벤트도 다시 발행하지 않는다.
    public void confirm(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        boolean alreadyConfirmed = delivery.getStatus() == DeliveryStatus.CONFIRMED;
        delivery.confirm();
        if (!alreadyConfirmed) {
            eventPublisher.publish(delivery);
        }
    }
}
