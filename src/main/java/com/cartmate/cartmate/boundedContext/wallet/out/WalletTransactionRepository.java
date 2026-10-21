package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(
            Long walletId,
            Pageable pageable
    );

    // 지갑의 거래 금액 합계를 요청한 유형별로 묶어 한 번에 조회한다. 거래가 없는 유형은 결과에 없다.
    @Query("select t.type as type, sum(t.amount) as total from WalletTransaction t "
            + "where t.wallet.id = :walletId and t.type in :types group by t.type")
    List<TypeTotal> sumAmountByWalletIdAndTypes(
            @Param("walletId") Long walletId, @Param("types") Collection<WalletTransactionType> types);

    interface TypeTotal {
        WalletTransactionType getType();

        Long getTotal();
    }

    Optional<WalletTransaction> findByTypeAndOrderIdAndReferenceIdAndReferenceType(
            WalletTransactionType transactionType, Long orderId, Long referenceId, ReferenceType referenceType
    );
}
