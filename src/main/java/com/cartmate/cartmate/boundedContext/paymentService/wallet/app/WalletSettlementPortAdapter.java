package com.cartmate.cartmate.boundedContext.paymentService.wallet.app;

import com.cartmate.cartmate.boundedContext.paymentService.shared.wallet.WalletSettlementPort;
import com.cartmate.cartmate.boundedContext.paymentService.shared.wallet.dto.SettleAndDepositRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletSettlementPortAdapter implements WalletSettlementPort {

    private final WalletFacade walletFacade;

    // 다른 모듈(payout)이 호출하는 진입점이다. 트랜잭션과 실제 처리는 WalletFacade와 WalletSettlementUseCase에 맡긴다.
    @Override
    public void settleAndDeposit(SettleAndDepositRequest request) {
        walletFacade.settleAndDeposit(request);
    }
}
