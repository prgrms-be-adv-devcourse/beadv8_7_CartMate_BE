package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryClaimCreateUseCase {

    // 판매자가 아직 처리 중이거나 승인되어 진행 중인 클레임. 이 상태가 있으면 새로 접수할 수 없다.
    private static final List<ClaimStatus> ACTIVE_STATUSES = List.of(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryClaimRepository deliveryClaimRepository;

    // 수령인(구매자) 본인이 배송완료된 배송에 교환·환불을 접수한다. 구매확정 이후에는 접수할 수 없다.
    public DeliveryClaimResponse create(
            Long deliveryId, Long memberId, ClaimType claimType, ClaimReason reason, String detail
    ) {
        validate(memberId, claimType, reason, detail);

        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!delivery.getMemberId().equals(memberId)) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }
        if (delivery.getStatus() != DeliveryStatus.DELIVERED) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS);
        }
        if (deliveryClaimRepository.existsByDeliveryIdAndStatusIn(deliveryId, ACTIVE_STATUSES)) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_ALREADY_EXISTS);
        }

        DeliveryClaim claim = DeliveryClaim.create(delivery, memberId, claimType, reason, normalize(detail));
        deliveryClaimRepository.save(claim);

        return DeliveryClaimResponse.from(claim);
    }

    private void validate(Long memberId, ClaimType claimType, ClaimReason reason, String detail) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        if (claimType == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_TYPE_REQUIRED);
        }
        if (reason == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_REASON_REQUIRED);
        }
        if (detail != null && detail.trim().length() > DeliveryClaim.DETAIL_MAX_LENGTH) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_DETAIL_TOO_LONG);
        }
    }

    // 앞뒤 공백을 제거하고 비어 있으면 null로 저장한다.
    private String normalize(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return detail.trim();
    }
}
