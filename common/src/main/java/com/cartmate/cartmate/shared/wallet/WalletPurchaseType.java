package com.cartmate.cartmate.shared.wallet;

// 지갑이 다루는 구매 유형. 홀딩이 속한 유형이고, 정산 입금 요청도 같은 유형으로 홀딩을 찾는다.
// 개인 주문과 공동물품 주문은 주문 ID를 각자 채번하므로 유형까지 같이 봐야 같은 주문을 구분할 수 있다.
public enum WalletPurchaseType {
    INDIVIDUAL_GROUP,   // 일반 주문 + 개인물품 공동구매 주문
    SHARED_GROUP        // 공동물품 구매 분담금
}
