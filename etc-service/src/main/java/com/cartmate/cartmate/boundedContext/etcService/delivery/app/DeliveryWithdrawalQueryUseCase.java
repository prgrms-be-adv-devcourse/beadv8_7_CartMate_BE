package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 회원 탈퇴 가능 여부 확인용 조회.
 * 거래가 끝났다 = 구매확정(CONFIRMED) 또는 취소(CANCELED). 그 전 상태는 모두 진행 중이다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryWithdrawalQueryUseCase {

    private static final Set<DeliveryStatus> NOT_FINISHED =
            EnumSet.of(DeliveryStatus.REQUESTED, DeliveryStatus.PREPARING, DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED);
    private static final Set<ClaimStatus> CLAIM_IN_PROGRESS = EnumSet.of(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryClaimRepository deliveryClaimRepository;

    public long countUnfinishedOfBuyer(Long memberId) {
        return deliveryRepository.countByMemberIdAndPurchaseTypeAndStatusIn(memberId, PurchaseType.INDIVIDUAL_GROUP, NOT_FINISHED);
    }

    public long countUnfinishedOfSharedOrders(Collection<Long> sharedGroupOrderIds) {
        if (sharedGroupOrderIds.isEmpty()) {
            return 0;
        }
        return deliveryRepository.countByOrderIdInAndPurchaseTypeAndStatusIn(sharedGroupOrderIds, PurchaseType.SHARED_GROUP, NOT_FINISHED);
    }

    public long countUnfinishedOfSeller(Long sellerId) {
        return deliveryRepository.countBySellerIdAndStatusIn(sellerId, NOT_FINISHED);
    }

    public long countClaimsInProgressOfBuyer(Long memberId) {
        return deliveryClaimRepository.countByMemberIdAndStatusIn(memberId, CLAIM_IN_PROGRESS);
    }

    public long countClaimsInProgressOfSeller(Long sellerId) {
        return deliveryClaimRepository.countByDeliverySellerIdAndStatusIn(sellerId, CLAIM_IN_PROGRESS);
    }
}