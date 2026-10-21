package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;

public record WalletBalanceResponse(
        Long walletId,
        Long balance,       // 바로 쓸 수 있는 잔액
        Long heldBalance    // 결제 후 판매자 정산이 끝나지 않아 홀딩에 보관 중인 금액(동결 금액)
) {
    public static WalletBalanceResponse from(Wallet wallet, long heldBalance) {
        return new WalletBalanceResponse(
                wallet.getId(),
                wallet.getBalance(),
                heldBalance
        );
    }
}
