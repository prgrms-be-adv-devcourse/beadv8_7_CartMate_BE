package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(
            Long walletId,
            Pageable pageable
    );
}
