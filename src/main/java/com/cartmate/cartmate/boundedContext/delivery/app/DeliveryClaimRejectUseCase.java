package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryClaimRejectUseCase {

    private final DeliveryClaimRepository deliveryClaimRepository;
    private final DeliveryClaimItemRepository deliveryClaimItemRepository;
    private final SellerQuery sellerQuery;

    // 해당 배송의 판매자가 접수된 교환·환불을 사유와 함께 거절한다.
    // 거절하면 처리 중인 클레임이 아니게 되어 구매확정이 풀리고, 구매자는 다시 접수할 수 있다.
    public SellerClaimResponse reject(Long claimId, Long memberId, String reason) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_REJECT_REASON_REQUIRED);
        }
        if (reason.length() > DeliveryClaim.DETAIL_MAX_LENGTH) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_REJECT_REASON_TOO_LONG);
        }

        DeliveryClaim claim = deliveryClaimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_NOT_FOUND));

        if (!sellerQuery.isSellerOwner(memberId, claim.getDelivery().getSellerId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        claim.reject(reason);

        return SellerClaimResponse.from(claim,
                deliveryClaimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(claim.getId())));
    }
}
