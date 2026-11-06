package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchasePayback;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderPurchaseConfirmation;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackSettlement;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.IndividualGroupPurchasePaybackRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.IndividualGroupPurchaseReplicaRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderPurchaseConfirmationRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchasePaybackUseCase {

    private final OrderRepository orderRepository;
    private final IndividualGroupPurchaseReplicaRepository replicaRepository;
    private final OrderPurchaseConfirmationRepository confirmationRepository;
    private final IndividualGroupPurchasePaybackRepository paybackRepository;

    void recordConfirmed(Long orderId, LocalDateTime confirmedAt, LocalDateTime now) {
        Long purchaseId = orderRepository.findById(orderId)
                .map(Order::getIndividualGroupPurchaseId)
                .orElse(null);
        IndividualGroupPurchaseReplica lockedReplica = purchaseId == null
                ? null
                : replicaRepository.findByIdForUpdate(purchaseId).orElse(null);
        if (!confirmationRepository.existsById(orderId)) {
            confirmationRepository.save(OrderPurchaseConfirmation.of(orderId, confirmedAt));
        }
        if (lockedReplica != null) {
            judgeLocked(lockedReplica, now);
        }
    }

    void judgeOfOrder(Long orderId, LocalDateTime now) {
        orderRepository.findById(orderId)
                .map(Order::getIndividualGroupPurchaseId)
                .ifPresent(purchaseId -> judge(purchaseId, now));
    }

    void judge(Long individualGroupPurchaseId, LocalDateTime now) {
        replicaRepository.findByIdForUpdate(individualGroupPurchaseId)
                .ifPresent(replica -> judgeLocked(replica, now));
    }

    private void judgeLocked(IndividualGroupPurchaseReplica replica, LocalDateTime now) {
        Long purchaseId = replica.getIndividualGroupPurchaseId();
        if (!replica.hasPaybackCondition() || replica.isOpen(now) || paybackRepository.existsById(purchaseId)) {
            return;
        }
        List<Order> orders = orderRepository.findAllByIndividualGroupPurchaseId(purchaseId);
        Set<Long> confirmedOrderIds = new HashSet<>();
        confirmationRepository.findAllById(orders.stream().map(Order::getId).toList())
                .forEach(confirmation -> confirmedOrderIds.add(confirmation.getOrderId()));

        PaybackSettlement.confirmedBuyerCount(orders, confirmedOrderIds).ifPresent(buyerCount ->
                paybackRepository.save(IndividualGroupPurchasePayback.judge(purchaseId,
                        replica.getPaybackMinBuyers(), replica.getPaybackAmount(), buyerCount, now)));
    }
}
