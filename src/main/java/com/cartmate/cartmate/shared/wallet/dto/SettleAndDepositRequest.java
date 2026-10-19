package com.cartmate.cartmate.shared.wallet.dto;

public record SettleAndDepositRequest(
        Long payoutId,
        Long orderId,
        Long sellerMemberId, // 입금받을 판매자의 회원 ID
        Long salesAmount,    // 홀딩에서 꺼낼 판매 금액
        Long payoutAmount    // 판매자에게 입금할 정산 금액(수수료 제외)
) {
}
