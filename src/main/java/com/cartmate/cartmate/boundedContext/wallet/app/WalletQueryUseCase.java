package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletQueryUseCase {
    private final WalletRepository walletRepository;

    public Wallet getWallet(Long memberId) {
        return walletRepository.findByMemberId(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("지갑이 존재하지 않습니다."));
    }
}
