package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletWithdrawOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletWithdrawOrderRepository extends JpaRepository<WalletWithdrawOrder, Long> {

    Optional<WalletWithdrawOrder> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
