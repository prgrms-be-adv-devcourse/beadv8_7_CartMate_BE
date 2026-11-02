package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSellerTrust;
import com.cartmate.cartmate.boundedContext.post.domain.SellerTrustLevel;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerTrustRepository;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesPostSyncSellerTrustUseCase {

    private final SalesPostSellerTrustRepository salesPostSellerTrustRepository;

    // 신뢰도 변경 이벤트로 판매자의 신뢰도 복제본을 만들거나 갱신한다.
    // 이미 있는 판매자는 이벤트의 계산 시각이 저장값보다 오래되면 무시한다.
    // 이벤트의 등급 문자열을 이곳에서 post의 등급으로 바꾸며, 모르는 등급이면 SELLER_TRUST_INVALID 예외가 발생한다.
    public void syncSellerTrust(SellerTrustScoreChangedEvent event) {
        SellerTrustLevel level = SellerTrustLevel.from(event.level());

        salesPostSellerTrustRepository.findById(event.sellerId()).ifPresentOrElse(
                trust -> trust.update(event.score(), level, event.calculatedAt()),
                () -> salesPostSellerTrustRepository.save(SalesPostSellerTrust.create(
                        event.sellerId(), event.score(), level, event.calculatedAt())));
    }
}
