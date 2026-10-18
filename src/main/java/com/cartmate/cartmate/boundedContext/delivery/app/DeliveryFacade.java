package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerDeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryFacade {

    private final DeliveryCreateUseCase deliveryCreateUseCase;
    private final DeliveryQueryUseCase deliveryQueryUseCase;
    private final DeliveryReadyUseCase deliveryReadyUseCase;
    private final DeliveryShipUseCase deliveryShipUseCase;
    private final DeliveryDeliverUseCase deliveryDeliverUseCase;
    private final DeliveryConfirmUseCase deliveryConfirmUseCase;
    private final DeliveryAutoConfirmUseCase deliveryAutoConfirmUseCase;
    private final DeliveryClaimCreateUseCase deliveryClaimCreateUseCase;
    private final DeliveryClaimQueryUseCase deliveryClaimQueryUseCase;
    private final DeliveryClaimApproveUseCase deliveryClaimApproveUseCase;
    private final DeliveryClaimCompleteUseCase deliveryClaimCompleteUseCase;

    @Transactional
    public void createDelivery(DeliveryCreateCommand createCommand) {
        deliveryCreateUseCase.create(createCommand);
    }

    @Transactional
    public DeliveryResponse readyDelivery(Long deliveryId, Long memberId, String carrier, String trackingNumber) {
        return deliveryReadyUseCase.ready(deliveryId, memberId, carrier, trackingNumber);
    }

    @Transactional
    public DeliveryResponse shipDelivery(Long deliveryId, Long memberId) {
        return deliveryShipUseCase.ship(deliveryId, memberId);
    }

    @Transactional
    public DeliveryResponse confirmDelivery(Long deliveryId, Long memberId) {
        return deliveryConfirmUseCase.confirm(deliveryId, memberId);
    }

    @Transactional
    public DeliveryClaimResponse createClaim(DeliveryClaimCreateCommand command) {
        return deliveryClaimCreateUseCase.create(command);
    }

    @Transactional
    public void autoConfirmDelivery(Long deliveryId) {
        deliveryAutoConfirmUseCase.confirm(deliveryId);
    }

    @Transactional
    public void deliverDelivery(Long deliveryId) {
        deliveryDeliverUseCase.deliver(deliveryId);
    }

    @Transactional
    public SellerClaimResponse approveClaim(Long claimId, Long memberId) {
        return deliveryClaimApproveUseCase.approve(claimId, memberId);
    }

    @Transactional
    public SellerClaimResponse completeClaim(Long claimId, Long memberId) {
        return deliveryClaimCompleteUseCase.complete(claimId, memberId);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryClaimResponse> getMyClaims(Long memberId, Pageable pageable) {
        return deliveryClaimQueryUseCase.getMyClaims(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SellerClaimResponse> getSellerClaims(
            Long memberId, List<ClaimStatus> statuses, ClaimType claimType, Pageable pageable
    ) {
        return deliveryClaimQueryUseCase.getSellerClaims(memberId, statuses, claimType, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> getMyDeliveries(Long memberId, Pageable pageable) {
        return deliveryQueryUseCase.getMyDeliveries(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SellerDeliveryResponse> getSellerDeliveries(Long memberId, DeliveryStatus status, Pageable pageable) {
        return deliveryQueryUseCase.getSellerDeliveries(memberId, status, pageable);
    }

    @Transactional(readOnly = true)
    public DeliveryDetailResponse getDeliveryDetails(Long deliveryId, Long memberId) {
        return deliveryQueryUseCase.getDeliveryDetails(deliveryId, memberId);
    }
}
