package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingSettlement;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingSettlementAllocator;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingSettlementRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.wallet.dto.SettleAndDepositRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletSettlementUseCase {

    private final WalletRepository walletRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingSettlementRepository holdingSettlementRepository;
    private final PlatformWalletProvider platformWalletProvider;
    private final LedgerRecorder ledgerRecorder;

    // 정산 한 건을 처리한다. 홀딩에서 꺼낸 판매 금액이 판매자 정산금과 플랫폼 수수료로 나뉘어 입금된다.
    // 1) 같은 정산 ID로 이미 처리됐으면 아무것도 바꾸지 않고 종료한다(금액이 다르면 예외).
    // 2) 주문의 홀딩을 잠그고, 남은 금액 비율로 판매 금액을 홀딩별로 나눠 꺼내 HoldingSettlement로 기록한다.
    // 3) 판매자 지갑에 정산금(SETTLEMENT)을 입금하고, 수수료가 있으면 플랫폼 지갑에 수수료(FEE)를 입금한다.
    // 어느 단계에서든 실패하면 예외가 발생하며, 같은 트랜잭션이라 모두 롤백된다.
    public void settleAndDeposit(SettleAndDepositRequest request) {
        validate(request);

        // 1) 이미 처리된 정산인지 확인한다.
        List<HoldingSettlement> existing = holdingSettlementRepository.findAllByPayoutId(request.payoutId());

        if (!existing.isEmpty()) {
            long settled = existing.stream().mapToLong(HoldingSettlement::getAmount).sum();

            if (settled != request.salesAmount()) {
                throw new ServiceException(WalletErrorCode.WALLET_TRANSACTION_AMOUNT_MISMATCH);
            }

            return;
        }

        // 2) 판매자 지갑과 주문의 홀딩(정산이 끝나거나 취소된 것은 제외)을 조회한다. 홀딩은 락을 걸어 동시 정산을 막는다.
        Wallet sellerWallet = walletRepository.findByMemberId(request.sellerMemberId())
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_NOT_FOUND));

        List<Holding> holdings = holdingRepository
                .findAllByOrderIdAndPurchaseTypeForUpdate(request.orderId(), request.purchaseType()).stream()
                .filter(holding -> holding.getStatus() == HoldingStatus.RESERVED)
                .toList();

        if (holdings.isEmpty()) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_NOT_FOUND);
        }

        // 홀딩별로 이미 정산에 쓰인 금액과 남은 금액을 구한 뒤, 판매 금액을 남은 금액 비율로 나눈다.
        List<Long> settledAmounts = holdings.stream()
                .map(holding -> holdingSettlementRepository.sumAmountByHoldingId(holding.getId()))
                .toList();

        List<Long> remainings = new ArrayList<>();
        for (int i = 0; i < holdings.size(); i++) {
            remainings.add(holdings.get(i).remainingAmount(settledAmounts.get(i)));
        }

        List<Long> shares = HoldingSettlementAllocator.allocate(remainings, request.salesAmount());

        // 홀딩마다 꺼낸 금액을 기록하고, 남은 금액이 없어진 홀딩은 정산 완료로 바꾼다.
        for (int i = 0; i < holdings.size(); i++) {
            if (shares.get(i) == 0) {
                continue;
            }

            Holding holding = holdings.get(i);

            holdingSettlementRepository.save(HoldingSettlement.create(holding, request.payoutId(), shares.get(i)));
            holding.closeIfExhausted(settledAmounts.get(i) + shares.get(i));
        }

        // 3) 판매자에게는 정산금을, 수수료(판매 금액 - 정산금)는 플랫폼 지갑에 입금한다.
        sellerWallet.deposit(request.payoutAmount());

        ledgerRecorder.record(
                sellerWallet, WalletTransactionType.SETTLEMENT, request.orderId(),
                request.payoutId(), ReferenceType.SETTLEMENT, null, request.payoutAmount());

        long fee = request.salesAmount() - request.payoutAmount();

        if (fee > 0) {
            Wallet platformWallet = platformWalletProvider.getPlatformWallet();
            platformWallet.deposit(fee);

            ledgerRecorder.record(
                    platformWallet, WalletTransactionType.FEE, request.orderId(),
                    request.payoutId(), ReferenceType.SETTLEMENT, null, fee);
        }
    }

    // 요청 값을 검증한다. 판매자 회원 ID, 정산 ID, 주문 ID는 필수이고, 판매 금액과 정산금은 0보다 커야 하며 정산금이 판매 금액을 넘을 수 없다.
    private void validate(SettleAndDepositRequest request) {
        if (request.sellerMemberId() == null) {
            throw new ServiceException(WalletErrorCode.WALLET_MEMBER_ID_REQUIRED);
        }

        if (request.payoutId() == null) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYOUT_ID_REQUIRED);
        }

        if (request.orderId() == null) {
            throw new ServiceException(WalletErrorCode.WALLET_ORDER_ID_REQUIRED);
        }

        if (request.purchaseType() == null) {
            throw new ServiceException(WalletErrorCode.WALLET_PURCHASE_TYPE_REQUIRED);
        }

        if (request.salesAmount() == null || request.salesAmount() <= 0
                || request.payoutAmount() == null || request.payoutAmount() <= 0
                || request.payoutAmount() > request.salesAmount()) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }
    }
}
