package com.cartmate.cartmate.boundedContext.order.out.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SharedGroupOrderRepositoryCustom {

    Optional<SharedGroupOrder> findByIdForUpdate(Long id);

    Optional<SharedGroupOrder> findWithDetailsById(Long id);

    List<SharedGroupOrder> findAllByParticipant(Long memberId);

    List<Long> findIdsToCancelByDeadline(LocalDateTime now);
}
