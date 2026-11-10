package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
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
    private final DomainEventPublisher eventPublisher;

    public SellerClaimResponse approve(Long claimId, Long memberId) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        DeliveryClaim claim = deliveryClaimRepository.findById(claimId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_NOT_FOUND));

        if (!sellerQuery.isSellerOwner(memberId, claim.getDelivery().getSellerId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        List<DeliveryClaimItem> claimItems =
                deliveryClaimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(claim.getId()));
        long refundAmount = refundAmountOf(claim, claimItems);

        claim.approve(refundAmount > 0);

        eventPublisher.publish(new DeliveryClaimApprovedEvent(
                claim.getId(),
                claim.getDelivery().getId(),
                claim.getDelivery().getOrderId(),
                claim.getDelivery().getPurchaseType(),
                claim.getMemberId(),
                claim.getDelivery().getSellerId(),
                claim.getClaimType().name(),
                refundAmount
        ));

        return SellerClaimResponse.from(claim, claimItems);
    }

    private long refundAmountOf(DeliveryClaim claim, List<DeliveryClaimItem> claimItems) {
        if (claim.getClaimType() != ClaimType.REFUND) {
            return 0L;
        }
        return claimItems.stream()
                .mapToLong(ci -> ci.getDeliveryItem().getUnitPrice() * ci.getQuantity())
                .sum();
    }
}
