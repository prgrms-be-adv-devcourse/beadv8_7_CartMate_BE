package com.cartmate.cartmate.boundedContext.order.out.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepositoryCustom {

    Optional<Order> findByIdForUpdate(Long id);

    Optional<Order> findWithItemsById(Long id);

    List<Order> findAllByMember(Long memberId);

    List<Long> findIdsToCancelByDeadline(LocalDateTime now);
}
