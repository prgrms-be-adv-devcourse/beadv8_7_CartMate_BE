package com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SharedGroupOrderRepository extends JpaRepository<SharedGroupOrder, Long>, SharedGroupOrderRepositoryCustom {

    boolean existsBySharedGroupPurchaseId(Long sharedGroupPurchaseId);

    Optional<SharedGroupOrder> findBySharedGroupPurchaseId(Long sharedGroupPurchaseId);

    @Query("select distinct o.id from SharedGroupOrder o join o.items i " +
            "where o.status in :statuses and i.productId in :productIds order by o.id")
    List<Long> findIdsByStatusInAndProductIds(@Param("statuses") Collection<SharedGroupOrderStatus> statuses,
                                              @Param("productIds") Collection<Long> productIds);

    @Query("select distinct o.id from SharedGroupOrder o join o.participants p " +
            "where p.memberId = :memberId and o.status in :statuses") List<Long> findIdsByParticipantAndStatusIn(@Param("memberId") Long memberId,
                                                                                                                 @Param("statuses") Collection<SharedGroupOrderStatus> statuses);
}
