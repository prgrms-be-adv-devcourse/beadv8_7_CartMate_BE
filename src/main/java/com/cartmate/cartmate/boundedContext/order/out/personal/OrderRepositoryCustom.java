package com.cartmate.cartmate.boundedContext.order.out.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepositoryCustom {

    Optional<Order> findByIdForUpdate(Long id);

    Optional<Order> findWithItemsById(Long id);

    List<Order> findAllByMember(Long memberId);

    List<Long> findIdsToCancelByDeadline(LocalDateTime now);

    boolean existsActiveOrderByIndividualGroupPurchaseAndProduct(Long individualGroupPurchaseId, Long productId);

    List<IndividualGroupPurchaseOrderLineView> findPaidOrderLines(Long individualGroupPurchaseId);

    Optional<Long> findPendingIndividualGroupOrderId(Long memberId, Long individualGroupPurchaseId);

    Optional<Long> findPendingPersonalOrderIdContaining(Long memberId, Collection<Long> inventoryIds);
}
