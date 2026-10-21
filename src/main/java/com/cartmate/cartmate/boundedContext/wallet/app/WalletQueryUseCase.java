package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletTransactionResponse;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingSettlementRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;

@Service
@RequiredArgsConstructor
public class WalletQueryUseCase {
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingSettlementRepository holdingSettlementRepository;

    // 회원 ID로 지갑을 조회하고 잔액 DTO를 반환한다. 홀딩 중인 금액(동결 금액)도 함께 담는다.
    public WalletBalanceResponse getWallet(Long memberId) {
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() ->
                        new ServiceException(WalletErrorCode.WALLET_NOT_FOUND));
        return WalletBalanceResponse.from(wallet, heldBalanceOf(wallet.getId()));
    }

    // 홀딩 중(RESERVED)인 홀딩의 남은 금액 합계다. 홀딩 한 건의 남은 금액은 홀딩 금액 - 환불 누적액 - 정산으로 꺼낸 금액이다.
    private long heldBalanceOf(Long walletId) {
        long unrefunded = holdingRepository.sumUnrefundedAmountByWalletIdAndStatus(walletId, HoldingStatus.RESERVED);
        long settled = holdingSettlementRepository.sumAmountByWalletIdAndHoldingStatus(walletId, HoldingStatus.RESERVED);
        return unrefunded - settled;
    }

    // 지갑 거래 내역을 생성 시각과 ID 기준 최신순으로 페이지 조회하고 DTO를 반환한다.
    public Page<WalletTransactionResponse> getTransactions(Long walletId, Pageable pageable) {
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDescIdDesc(walletId, pageable)
                .map(WalletTransactionResponse::from);
    }
}
