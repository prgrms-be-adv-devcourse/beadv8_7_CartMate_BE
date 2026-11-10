package com.cartmate.cartmate.boundedContext.paymentService.payout.app;

import com.cartmate.cartmate.boundedContext.paymentService.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.paymentService.payout.domain.PayoutSeller;
import com.cartmate.cartmate.boundedContext.paymentService.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.paymentService.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.boundedContext.paymentService.payout.out.PayoutRepository;
import com.cartmate.cartmate.boundedContext.paymentService.payout.out.PayoutSellerRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import com.cartmate.cartmate.shared.wallet.WalletSettlementPort;
import com.cartmate.cartmate.shared.wallet.dto.SettleAndDepositRequest;
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
    // 주문 키 분산락을 얻은 뒤 연 트랜잭션에서 정산서를 읽어야 아래 "대기가 아니면 반환" 검사가 의미 있다.
    public void pay(Long payoutId, LocalDateTime now) {
        Payout payout = payoutRepository.findById(payoutId)
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

        walletSettlementPort.settleAndDeposit(new SettleAndDepositRequest(
                payout.getId(), payout.getOrderId(), WalletPurchaseType.valueOf(payout.getPurchaseType().name()),
                seller.getMemberId(),
                payout.getSalesAmount(), payout.getPayoutAmount()
        ));

        payout.complete(now);
    }

    // 지급할 정산의 주문 키 분산락 이름. 락을 잡기 전에 읽으므로 상태 판단에는 쓰지 않는다(주문·구매 유형은 바뀌지 않는다).
    public String holdingLockKey(Long payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ServiceException(PayoutErrorCode.PAYOUT_NOT_FOUND));
        return HoldingLockKeys.of(payout.getPurchaseType().name(), payout.getOrderId());
    }
}
