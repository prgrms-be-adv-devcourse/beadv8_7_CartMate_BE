package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletChargeOrderRepository extends JpaRepository<WalletChargeOrder, Long> {

    Optional<WalletChargeOrder> findByIdempotencyKey(String idempotencyKey);

    Optional<WalletChargeOrder> findByOrderNumber(String orderNumber);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
