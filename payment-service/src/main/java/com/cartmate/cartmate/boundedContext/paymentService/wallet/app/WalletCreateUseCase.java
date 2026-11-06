package com.cartmate.cartmate.boundedContext.paymentService.wallet.app;

import com.cartmate.cartmate.boundedContext.paymentService.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.paymentService.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.paymentService.wallet.out.WalletRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletCreateUseCase {
    private final WalletRepository walletRepository;

    // 회원의 지갑을 생성한다. 이미 존재하면 그대로 둔다 (가입 이벤트를 다시 받아도 성공으로 끝나야 재시도가 멈춘다).
    // 같은 회원으로 동시에 들어와 둘 다 없다고 보면 member_id 유니크로 하나가 실패 → 재시도 때 여기서 건너뛴다.
    @Transactional
    public void createWallet(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_MEMBER_ID_REQUIRED);
        }

        if (walletRepository.existsByMemberId(memberId)) {
            return;
        }

        Wallet wallet = Wallet.createWallet(memberId);
        walletRepository.save(wallet);
    }
}
