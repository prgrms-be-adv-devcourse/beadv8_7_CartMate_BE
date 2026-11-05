package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplica;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderReplicaRepository;
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

    // 이 이벤트에는 주문 유형이 없다. 사본이 있는 주문(= 개인물품 공동구매 주문)만 반영하고 개인 주문은 그냥 지나간다.
    // 생성 · 결제 이벤트가 재시도로 밀려 이 이벤트가 먼저 오면 반영할 항목이 없어 놓친다(드묾, 카드에 기록)
    void applySellerItemsCanceled(OrderSellerItemsCanceledEvent event) {
        replicaRepository.findById(event.orderId())
                .ifPresent(replica -> replica.applySellerItemsCanceled(event.sellerId()));
    }

    // 릴레이는 보통 한 인스턴스에서 한 줄씩 넘긴다. 락 시간(30초)을 넘겨 두 인스턴스가 같은 행을 동시에 만들면
    // 한쪽이 PK 충돌로 실패하고, 릴레이가 다시 시도할 때 만들어진 행을 찾아 같은 결과로 수렴한다
    private IndividualGroupPurchaseOrderReplica getOrCreate(Long orderId) {
        return replicaRepository.findById(orderId)
                .orElseGet(() -> replicaRepository.save(IndividualGroupPurchaseOrderReplica.create(orderId)));
    }
}
