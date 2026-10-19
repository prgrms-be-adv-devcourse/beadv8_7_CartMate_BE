package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutSeller;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutSellerRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.wallet.WalletSettlementPort;
import com.cartmate.cartmate.shared.wallet.dto.PayoutSettlementRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PayoutPayUseCase {

    private final PayoutRepository payoutRepository;
    private final PayoutSellerRepository payoutSellerRepository;
    private final WalletSettlementPort walletSettlementPort;

    // 대기 중인 정산의 정산금을 판매자 지갑에 입금하고 지급 완료로 바꾼다.
    // 이미 처리된 정산은 건너뛰고, 정산 금액이 0원이면 입금할 돈이 없으므로 입금 없이 바로 완료 처리한다.
    // 입금이 실패하면 예외가 전파되어 정산은 대기 상태로 남고 다음 실행 때 다시 시도한다.
    public void pay(Long payoutId, LocalDateTime now) {
        Payout payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ServiceException(PayoutErrorCode.PAYOUT_NOT_FOUND));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            return;
        }

        if (payout.getPayoutAmount() == 0) {
            payout.complete(now);
            return;
        }

        PayoutSeller seller = payoutSellerRepository.findById(payout.getSellerId())
                .orElseThrow(() -> new ServiceException(PayoutErrorCode.PAYOUT_SELLER_NOT_FOUND));

        walletSettlementPort.settleAndDeposit(new PayoutSettlementRequest(
                payout.getId(), payout.getOrderId(), seller.getMemberId(),
                payout.getSalesAmount(), payout.getPayoutAmount()
        ));

        payout.complete(now);
    }
}
