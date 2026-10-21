package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(
            Long walletId,
            Pageable pageable
    );

    // 지갑의 해당 유형 거래 금액 합계. 거래가 없으면 0이다.
    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t where t.wallet.id = :walletId and t.type = :type")
    long sumAmountByWalletIdAndType(@Param("walletId") Long walletId, @Param("type") WalletTransactionType type);

    Optional<WalletTransaction> findByTypeAndOrderIdAndReferenceIdAndReferenceType(
            WalletTransactionType transactionType, Long orderId, Long referenceId, ReferenceType referenceType
    );
}
