package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimApprovedEvent;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryClaimApproveUseCase {

    private final DeliveryClaimRepository deliveryClaimRepository;
    private final DeliveryClaimItemRepository deliveryClaimItemRepository;
    private final SellerQuery sellerQuery;
    private final EventPublisher eventPublisher;

    // 해당 배송의 판매자가 접수된 교환·환불을 수락한다.
    // 환불이면 payment가 같은 트랜잭션에서 환불하며, 환불이 실패하면 수락도 함께 롤백된다.
    public SellerClaimResponse approve(Long claimId, Long memberId) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        DeliveryClaim claim = deliveryClaimRepository.findById(claimId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_NOT_FOUND));

        if (!sellerQuery.isSellerOwner(memberId, claim.getDelivery().getSellerId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        claim.approve();

        List<DeliveryClaimItem> claimItems =
                deliveryClaimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(claim.getId()));

        eventPublisher.publish(new DeliveryClaimApprovedEvent(
                claim.getId(),
                claim.getDelivery().getId(),
                claim.getDelivery().getOrderId(),
                claim.getDelivery().getPurchaseType(),
                claim.getMemberId(),
                claim.getDelivery().getSellerId(),
                claim.getClaimType().name(),
                refundAmountOf(claim, claimItems)
        ));

        return SellerClaimResponse.from(claim, claimItems);
    }

    // 환불이면 요청 상품의 단가 × 요청 수량 합계, 교환이면 0
    private long refundAmountOf(DeliveryClaim claim, List<DeliveryClaimItem> claimItems) {
        if (claim.getClaimType() != ClaimType.REFUND) {
            return 0L;
        }
        return claimItems.stream()
                .mapToLong(ci -> ci.getDeliveryItem().getUnitPrice() * ci.getQuantity())
                .sum();
    }
}