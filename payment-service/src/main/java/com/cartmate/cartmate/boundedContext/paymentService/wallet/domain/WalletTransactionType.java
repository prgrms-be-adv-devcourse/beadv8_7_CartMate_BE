package com.cartmate.cartmate.boundedContext.paymentService.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletTransactionType {
    CHARGE("충전"),             // 예치금 충전
    USE("구매"),                // 상품 구매
    REFUND("환불"),             // 상품 결제 취소에 따른 예치금 복구
    SETTLEMENT("정산금 입금"),  // 판매 정산금 입금
    FEE("수수료"),              // 판매 수수료 수입 (플랫폼 지갑)
    WITHDRAW("출금");           // 예치금 출금

    private final String label;
}
