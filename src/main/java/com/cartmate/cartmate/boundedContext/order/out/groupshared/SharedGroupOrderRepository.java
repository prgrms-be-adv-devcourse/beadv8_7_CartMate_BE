package com.cartmate.cartmate.boundedContext.order.out.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedGroupOrderRepository extends JpaRepository<SharedGroupOrder, Long>, SharedGroupOrderRepositoryCustom {

    boolean existsBySharedGroupPurchaseId(Long sharedGroupPurchaseId);
}
