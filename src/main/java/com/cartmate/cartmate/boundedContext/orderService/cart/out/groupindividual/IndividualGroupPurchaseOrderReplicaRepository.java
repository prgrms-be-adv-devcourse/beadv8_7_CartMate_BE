package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndividualGroupPurchaseOrderReplicaRepository
        extends JpaRepository<IndividualGroupPurchaseOrderReplica, Long>, IndividualGroupPurchaseOrderReplicaRepositoryCustom {

    List<IndividualGroupPurchaseOrderReplica> findAllByIndividualGroupPurchaseId(Long individualGroupPurchaseId);
}
