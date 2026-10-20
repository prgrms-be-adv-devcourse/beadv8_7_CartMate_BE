package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryConfirmUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryClaimRepository deliveryClaimRepository;
    private final DeliveryConfirmedEventPublisher eventPublisher;

    // 배송의 수령인(구매자) 본인이 배송완료 상태의 배송을 구매확정으로 바꾸고 구매확정 이벤트를 발행한다.
    public DeliveryResponse confirm(Long deliveryId, Long memberId) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!delivery.getMemberId().equals(memberId)) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        // 처리 중인 교환·환불이 있으면 환불될 상품이 정산에 섞이므로 클레임이 끝날 때까지 구매확정할 수 없다.
        if (delivery.getStatus() == DeliveryStatus.DELIVERED && deliveryClaimRepository.hasActiveClaim(delivery.getId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_IN_PROGRESS);
        }

        if (delivery.confirm()) {   // 상태가 변경되었을 때만 이벤트 발행
            eventPublisher.publish(delivery);
        }

        return DeliveryResponse.from(delivery);
    }
}
