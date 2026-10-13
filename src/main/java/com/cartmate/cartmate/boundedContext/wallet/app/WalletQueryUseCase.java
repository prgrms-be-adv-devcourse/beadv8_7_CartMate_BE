package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletTransactionResponse;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletQueryUseCase {
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletBalanceResponse getWallet(Long memberId) {
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("지갑이 존재하지 않습니다."));
        return WalletBalanceResponse.from(wallet);
    }

    public Page<WalletTransactionResponse> getTransactions(Long walletId, Pageable pageable) {
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDescIdDesc(walletId, pageable)
                .map(WalletTransactionResponse::from);
    }
}
