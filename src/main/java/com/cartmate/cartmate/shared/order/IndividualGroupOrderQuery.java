package com.cartmate.cartmate.shared.order;

// order가 cart에 내주는 조회 창구. cart는 order 내부 대신 이것만 의존한다 (물리 분리 시 API로 교체)
public interface IndividualGroupOrderQuery {

    // 개인물품 공동구매의 이 상품에 결제 대기 · 결제 완료 주문이 있는지. 상품을 공동구매에서 빼도 되는지 판단할 때 쓴다
    boolean hasActiveOrder(Long individualGroupPurchaseId, Long productId);
}
