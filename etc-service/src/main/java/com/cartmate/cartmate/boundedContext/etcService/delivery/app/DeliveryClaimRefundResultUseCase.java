package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryClaimRefundResultUseCase {

    private final DeliveryClaimRepository deliveryClaimRepository;

    public void complete(Long claimId) {
        find(claimId).completeRefund();
    }

    public void fail(Long claimId) {
        find(claimId).failRefund();
    }

    private DeliveryClaim find(Long claimId) {
        return deliveryClaimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_NOT_FOUND));
    }
}
