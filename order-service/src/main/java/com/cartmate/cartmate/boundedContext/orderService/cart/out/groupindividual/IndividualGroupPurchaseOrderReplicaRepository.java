package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplica;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndividualGroupPurchaseOrderReplicaRepository
        extends JpaRepository<IndividualGroupPurchaseOrderReplica, Long>, IndividualGroupPurchaseOrderReplicaRepositoryCustom {
}
