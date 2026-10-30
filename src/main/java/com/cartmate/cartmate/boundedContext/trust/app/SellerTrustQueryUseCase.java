package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.dto.SellerTrustResponse;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerTrustQueryUseCase {

    private final SellerTrustScoreRepository sellerTrustScoreRepository;

    // 판매자의 신뢰도 점수와 등급을 조회한다. 점수 행이 없으면 예외 없이 점수 없는 NEW로 응답한다.
    public SellerTrustResponse getSellerTrust(Long sellerId) {
        return sellerTrustScoreRepository.findBySellerId(sellerId)
                .map(SellerTrustResponse::from)
                .orElseGet(() -> SellerTrustResponse.unrated(sellerId));
    }
}
