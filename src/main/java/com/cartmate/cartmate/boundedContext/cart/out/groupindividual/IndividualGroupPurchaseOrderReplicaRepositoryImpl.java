package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.List;

import static com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.QIndividualGroupPurchaseOrderLineReplica.individualGroupPurchaseOrderLineReplica;
import static com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.QIndividualGroupPurchaseOrderReplica.individualGroupPurchaseOrderReplica;

public class IndividualGroupPurchaseOrderReplicaRepositoryImpl implements IndividualGroupPurchaseOrderReplicaRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public IndividualGroupPurchaseOrderReplicaRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<IndividualGroupPurchaseOrderLine> findPaidLines(Long individualGroupPurchaseId) {
        return queryFactory
                .select(Projections.constructor(IndividualGroupPurchaseOrderLine.class,
                        individualGroupPurchaseOrderReplica.orderId,
                        individualGroupPurchaseOrderReplica.buyerMemberId,
                        individualGroupPurchaseOrderLineReplica.productId,
                        individualGroupPurchaseOrderLineReplica.inventoryId,
                        individualGroupPurchaseOrderLineReplica.quantity,
                        individualGroupPurchaseOrderLineReplica.unitPrice,
                        individualGroupPurchaseOrderReplica.orderedAt
                ))
                .from(individualGroupPurchaseOrderLineReplica)
                .join(individualGroupPurchaseOrderLineReplica.order, individualGroupPurchaseOrderReplica)
                .where(
                        individualGroupPurchaseOrderReplica.individualGroupPurchaseId.eq(individualGroupPurchaseId),
                        individualGroupPurchaseOrderReplica.status.eq(IndividualGroupPurchaseOrderReplicaStatus.PAID),
                        individualGroupPurchaseOrderLineReplica.canceled.isFalse()
                )
                .orderBy(individualGroupPurchaseOrderReplica.orderId.asc(),
                        individualGroupPurchaseOrderLineReplica.orderItemId.asc())
                .fetch();
    }
}
