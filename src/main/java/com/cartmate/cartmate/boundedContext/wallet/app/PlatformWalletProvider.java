package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlatformWalletProvider {

    private final WalletRepository walletRepository;
    private final Long platformMemberId;

    public PlatformWalletProvider(
            WalletRepository walletRepository,
            @Value("${custom.wallet.platform-member-id}") Long platformMemberId
    ) {
        this.walletRepository = walletRepository;
        this.platformMemberId = platformMemberId;
    }

    // 판매 수수료를 받는 플랫폼 지갑을 반환한다. 설정한 관리자 회원의 지갑이 아직 없으면 만든다.
    public Wallet getPlatformWallet() {
        return walletRepository.findByMemberId(platformMemberId)
                .orElseGet(() -> walletRepository.save(Wallet.createWallet(platformMemberId)));
    }
}
