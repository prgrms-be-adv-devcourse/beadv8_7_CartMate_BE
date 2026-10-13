package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeOrderRepository;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletChargeUseCase {
    private final WalletRepository walletRepository;
    private final WalletChargeOrderRepository walletChargeOrderRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public void charge(Long walletId, Long amount) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        wallet.charge(amount);

        WalletTransaction walletTransaction = WalletTransaction.create(
                wallet, WalletTransactionType.CHARGE, amount, wallet.getBalance(),
                null, null
        );

        walletTransactionRepository.save(walletTransaction);
    }

    public WalletChargeOrder createChargeOrder(Long walletId, Long amount, String idempotencyKey) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        return walletChargeOrderRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() ->
                        {
                            WalletChargeOrder walletChargeOrder = WalletChargeOrder.create(
                                    wallet, amount, idempotencyKey
                            );

                            return walletChargeOrderRepository.save(walletChargeOrder);
                        }
                );
    }
}
