package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByMemberId(Long memberId);

    boolean existsByMemberId(Long MemberId);
}
