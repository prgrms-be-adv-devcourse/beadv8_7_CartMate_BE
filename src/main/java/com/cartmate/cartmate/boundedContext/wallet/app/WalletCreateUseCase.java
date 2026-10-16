package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletCreateUseCase {
    private final WalletRepository walletRepository;

    // 회원의 지갑을 생성한다. 이미 존재하면 생성하지 않는다.
    @Transactional
    public void createWallet(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_MEMBER_ID_REQUIRED);
        }

        if (walletRepository.existsByMemberId(memberId)) {
            throw new ServiceException(WalletErrorCode.WALLET_ALREADY_EXISTS);
        }

        Wallet wallet = Wallet.createWallet(memberId);
        walletRepository.save(wallet);
    }
}
