package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletChargeOrderRepository extends JpaRepository<WalletChargeOrder, Long> {
}
