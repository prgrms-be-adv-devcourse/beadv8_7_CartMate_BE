package com.cartmate.cartmate.boundedContext.payout.out;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    List<Payout> findAllByOrderItemIdIn(Collection<Long> orderItemIds);
}
