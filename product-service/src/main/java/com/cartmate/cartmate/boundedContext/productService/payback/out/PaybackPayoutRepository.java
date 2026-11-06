package com.cartmate.cartmate.boundedContext.productService.payback.out;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayout;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaybackPayoutRepository extends JpaRepository<PaybackPayout, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaybackPayout p where p.id = :id")
    Optional<PaybackPayout> findByIdForUpdate(@Param("id") Long id);

    @Query("select p.id from PaybackPayout p where p.status = :status order by p.id")
    List<Long> findIdsByStatus(@Param("status") PaybackPayoutStatus status, Limit limit);

    List<PaybackPayout> findAllByStatusOrderByIdDesc(PaybackPayoutStatus status, Limit limit);
}
