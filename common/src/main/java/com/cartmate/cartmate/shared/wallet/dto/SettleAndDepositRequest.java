package com.cartmate.cartmate.shared.wallet.dto;

import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;

public record SettleAndDepositRequest(
        Long payoutId,
        Long orderId,
        WalletPurchaseType purchaseType, // 개인 주문과 공동물품 주문은 주문 ID가 겹칠 수 있어 구분한다.
        Long sellerMemberId, // 입금받을 판매자의 회원 ID
        Long salesAmount,    // 홀딩에서 꺼낼 판매 금액
        Long payoutAmount    // 판매자에게 입금할 정산 금액(수수료 제외)
) {
}
