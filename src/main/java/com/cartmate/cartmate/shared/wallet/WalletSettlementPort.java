package com.cartmate.cartmate.shared.wallet;

import com.cartmate.cartmate.shared.wallet.dto.PayoutSettlementRequest;

public interface WalletSettlementPort {

    // 홀딩에서 판매 금액을 꺼내 판매자 지갑에 정산금을 입금한다. 같은 정산 ID로 다시 호출하면 이미 처리된 것으로 보고 정상 종료한다.
    // 처리하지 못하면 예외를 던지며, 이때 호출한 쪽은 정산을 완료 처리하지 않는다.
    void settleAndDeposit(PayoutSettlementRequest request);
}
