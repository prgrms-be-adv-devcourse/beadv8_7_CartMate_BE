package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.event.DeliverySellerCanceledEvent;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeliverySellerReasonCancelUseCase {

    private final DeliveryRepository deliveryRepository;
    private final SellerQuery sellerQuery;
    private final DomainEventPublisher eventPublisher;

    // 판매자가 자기 배송을 사유와 함께 취소하고, 실제로 취소했을 때만 같은 트랜잭션에서 주문 서버에 알린다.
    // 주문 서버는 이 이벤트를 받아 그 판매자 항목을 취소하고 환불한다.
    public DeliveryResponse cancel(Long deliveryId, Long memberId, DeliveryCancelReason reason) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!sellerQuery.isSellerOwner(memberId, delivery.getSellerId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        if (delivery.cancelBySeller(reason, LocalDateTime.now())) {
            eventPublisher.publish(new DeliverySellerCanceledEvent(
                    delivery.getPurchaseType(), delivery.getOrderId(), delivery.getSellerId(),
                    reason, delivery.getCanceledAt()));
        }
        return DeliveryResponse.from(delivery);
    }
}
