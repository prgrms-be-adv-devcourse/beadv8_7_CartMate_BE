package com.cartmate.cartmate.boundedContext.trust.domain;

import com.cartmate.cartmate.boundedContext.trust.exception.TrustErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

// 판매자 한 명의 거래 이력 집계값. 점수 계산의 입력이다.
public record TrustMetrics(
        int confirmedCount,   // 구매확정 건수 (배송 완료 후 구매확정된 배송 건)
        int faultClaimCount   // 판매자 귀책 클레임 건수 (PRODUCT_DEFECT, WRONG_DELIVERY)
) {

    // 값이 하나라도 음수이면 TRUST_INVALID_METRICS 예외가 발생한다.
    public TrustMetrics {
        if (confirmedCount < 0 || faultClaimCount < 0) {
            throw new ServiceException(TrustErrorCode.TRUST_INVALID_METRICS);
        }
    }
}
