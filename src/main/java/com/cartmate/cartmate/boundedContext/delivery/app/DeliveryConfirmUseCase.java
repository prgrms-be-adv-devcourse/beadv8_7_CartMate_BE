package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryConfirmUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryConfirmedEventPublisher eventPublisher;

    // 배송의 수령인(구매자) 본인이 배송완료 상태의 배송을 구매확정으로 바꾸고 구매확정 이벤트를 발행한다.
    public DeliveryResponse confirm(Long deliveryId, Long memberId) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!delivery.getMemberId().equals(memberId)) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        if (delivery.confirm()) {   // 상태가 변경되었을 때만 이벤트 발행
            eventPublisher.publish(delivery);
        }

        return DeliveryResponse.from(delivery);
    }
}
