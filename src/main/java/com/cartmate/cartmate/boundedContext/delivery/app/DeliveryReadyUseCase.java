package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryReadyUseCase {

    private final DeliveryRepository deliveryRepository;
    private final SellerQuery sellerQuery;

    // 해당 배송의 판매자가 택배사와 송장번호를 등록해 배송준비 상태로 바꾼다.
    public DeliveryResponse ready(Long deliveryId, Long memberId, String carrier, String trackingNumber) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!sellerQuery.isSellerOwner(memberId, delivery.getSellerId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        delivery.prepare(carrier, trackingNumber);

        return DeliveryResponse.from(delivery);
    }
}
