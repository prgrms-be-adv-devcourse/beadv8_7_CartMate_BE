package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustHistory;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustHistoryType;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustHistoryRepository;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimCompletedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SellerTrustHistoryRecordUseCase {

    // 판매자 귀책으로 보는 클레임 사유(delivery의 ClaimReason 이름). 단순 변심·사이즈 불일치·기타는 판매자 책임이 아니다.
    // delivery 도메인의 enum을 import하지 않고 이벤트가 전하는 문자열로 비교해 컨텍스트 사이 결합을 피한다.
    private static final Set<String> FAULT_CLAIM_REASONS = Set.of("PRODUCT_DEFECT", "WRONG_DELIVERY");

    private final SellerTrustHistoryRepository sellerTrustHistoryRepository;

    // 구매확정 이벤트를 구매확정 이력으로 남긴다. 식별 키는 배송 ID라 같은 배송의 이벤트가 다시 와도 한 번만 남는다.
    public void recordPurchaseConfirmed(DeliveryConfirmedEvent event) {
        record(event.sellerId(), TrustHistoryType.PURCHASE_CONFIRMED,
                String.valueOf(event.deliveryId()), event.confirmedAt());
    }

    // 클레임 처리 완료 이벤트가 판매자 귀책 사유일 때만 귀책 클레임 이력으로 남긴다. 식별 키는 클레임 ID다.
    // 승인 시점이 아니라 완료 시점에 세는 이유는, 클레임이 끝나야 그 배송이 구매확정될 수 있어 구매확정 건수(분모)와 시점이 맞기 때문이다.
    // 완료 요청이 재시도되면 같은 클레임의 이벤트가 다시 발행되는데, 이때도 한 번만 남는다.
    public void recordClaimCompleted(DeliveryClaimCompletedEvent event) {
        if (!FAULT_CLAIM_REASONS.contains(event.claimReason())) {
            return;
        }

        record(event.sellerId(), TrustHistoryType.FAULT_CLAIM,
                String.valueOf(event.claimId()), event.completedAt());
    }

    // 같은 유형·식별 키의 이력이 이미 있으면 아무것도 하지 않는다.
    // 동시에 두 번 들어오는 경합은 이 확인을 함께 통과할 수 있지만, (type, source_key) unique 제약이 막는다.
    private void record(Long sellerId, TrustHistoryType type, String sourceKey, LocalDateTime occurredAt) {
        if (sellerTrustHistoryRepository.existsByTypeAndSourceKey(type, sourceKey)) {
            return;
        }

        sellerTrustHistoryRepository.save(SellerTrustHistory.create(sellerId, type, sourceKey, occurredAt));
    }
}
