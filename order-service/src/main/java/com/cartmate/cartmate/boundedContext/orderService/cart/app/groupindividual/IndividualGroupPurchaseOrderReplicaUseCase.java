package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplica;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseOrderReplicaRepository;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseOrderReplicaUseCase {

    private final IndividualGroupPurchaseOrderReplicaRepository replicaRepository;

    void applyCreated(OrderCreatedEvent event) {
        List<IndividualGroupPurchaseOrderReplica.Line> lines = event.items().stream()
                .map(item -> new IndividualGroupPurchaseOrderReplica.Line(item.orderItemId(), item.productId(),
                        item.inventoryId(), item.sellerId(), item.quantity(), item.unitPrice()))
                .toList();
        getOrCreate(event.orderId())
                .applyCreated(event.individualGroupPurchaseId(), event.memberId(), event.createdAt(), lines);
    }

    void applyPaid(OrderPaidEvent event) {
        List<IndividualGroupPurchaseOrderReplica.Line> lines = event.items().stream()
                .map(item -> new IndividualGroupPurchaseOrderReplica.Line(item.orderItemId(), item.productId(),
                        item.inventoryId(), item.sellerId(), item.quantity(), item.unitPrice()))
                .toList();
        getOrCreate(event.orderId())
                .applyPaid(event.individualGroupPurchaseId(), event.memberId(), event.paidAt(), lines);
    }

    void applyCanceled(OrderCanceledEvent event) {
        getOrCreate(event.orderId()).applyCanceled();
    }

    void applySellerItemsCanceled(OrderSellerItemsCanceledEvent event) {
        replicaRepository.findById(event.orderId())
                .ifPresent(replica -> replica.applySellerItemsCanceled(event.sellerId()));
    }

    private IndividualGroupPurchaseOrderReplica getOrCreate(Long orderId) {
        return replicaRepository.findById(orderId)
                .orElseGet(() -> replicaRepository.save(IndividualGroupPurchaseOrderReplica.create(orderId)));
    }
}
