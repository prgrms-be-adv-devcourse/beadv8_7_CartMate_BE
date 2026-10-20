package com.cartmate.cartmate.boundedContext.wallet.domain;

public enum WalletTransactionType {
    CHARGE,       // 예치금 충전
    USE,          // 상품 구매
    REFUND,       // 상품 결제 취소에 따른 예치금 복구
    SETTLEMENT,   // 판매 정산금 입금
    FEE,          // 판매 수수료 수입 (플랫폼 지갑)
    WITHDRAW      // 예치금 출금
}
