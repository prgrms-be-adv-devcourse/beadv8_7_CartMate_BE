package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimCompletedEvent;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryClaimCompleteUseCase {

    private final DeliveryClaimRepository deliveryClaimRepository;
    private final DeliveryClaimItemRepository deliveryClaimItemRepository;
    private final SellerQuery sellerQuery;
    private final EventPublisher eventPublisher;

    // 해당 배송의 판매자가 수락한 교환·환불을 처리 완료로 바꾸고 완료 이벤트를 발행한다.
    // 이미 완료된 클레임을 다시 완료해도 성공하며 이벤트도 다시 발행되는데, 받는 쪽이 같은 클레임을 한 번만 반영한다.
    public SellerClaimResponse complete(Long claimId, Long memberId) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        DeliveryClaim claim = deliveryClaimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_NOT_FOUND));

        if (!sellerQuery.isSellerOwner(memberId, claim.getDelivery().getSellerId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        claim.complete();

        eventPublisher.publish(new DeliveryClaimCompletedEvent(
                claim.getId(),
                claim.getDelivery().getId(),
                claim.getDelivery().getSellerId(),
                claim.getClaimType().name(),
                claim.getReason().name(),
                claim.getCompletedAt()
        ));

        return SellerClaimResponse.from(claim,
                deliveryClaimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(claim.getId())));
    }
}
