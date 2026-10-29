package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimCompletedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrustFacade {

    private final SellerTrustHistoryRecordUseCase sellerTrustHistoryRecordUseCase;

    // 구매확정 이벤트를 받아 구매확정 이력을 남긴다.
    @Transactional
    public void recordPurchaseConfirmed(DeliveryConfirmedEvent event) {
        sellerTrustHistoryRecordUseCase.recordPurchaseConfirmed(event);
    }

    // 클레임 처리 완료 이벤트를 받아 판매자 귀책이면 귀책 클레임 이력을 남긴다.
    @Transactional
    public void recordClaimCompleted(DeliveryClaimCompletedEvent event) {
        sellerTrustHistoryRecordUseCase.recordClaimCompleted(event);
    }
}
