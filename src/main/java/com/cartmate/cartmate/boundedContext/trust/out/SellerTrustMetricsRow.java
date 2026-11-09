package com.cartmate.cartmate.boundedContext.trust.out;

// 판매자 한 명의 구매확정·귀책 클레임 건수. aggregateBySeller 쿼리의 별칭과 이름이 같아야 값이 채워진다.
public interface SellerTrustMetricsRow {

    Long getSellerId();

    Long getConfirmedCount();

    Long getFaultClaimCount();
}
