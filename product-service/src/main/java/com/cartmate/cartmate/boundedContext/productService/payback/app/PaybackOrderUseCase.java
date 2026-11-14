package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.IndividualGroupPurchasePayback;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackOrder;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackOrderItem;
import com.cartmate.cartmate.boundedContext.productService.payback.out.PaybackOrderRepository;
import com.cartmate.cartmate.shared.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
class PaybackOrderUseCase {

    private final PaybackOrderRepository orderRepository;
    private final GroupPurchaseSnapshotUseCase groupPurchaseSnapshotUseCase;
    private final PaybackJudgeUseCase paybackJudgeUseCase;

    void applyPlaced(Long orderId, Long individualGroupPurchaseId, Long buyerMemberId, List<PaybackOrderItem> items,
                     OrderStatus status, LocalDateTime now) {
        IndividualGroupPurchasePayback payback = groupPurchaseSnapshotUseCase.lock(individualGroupPurchaseId);
        lockOrder(orderId).applyPlaced(individualGroupPurchaseId, buyerMemberId, items, status);
        paybackJudgeUseCase.judgeLocked(payback, now);
    }

    void applyCanceled(Long orderId, LocalDateTime now) {
        apply(orderId, PaybackOrder::applyCanceled, now);
    }

    void applySellerItemsCanceled(Long orderId, Long sellerId, LocalDateTime now) {
        apply(orderId, order -> order.applySellerItemsCanceled(sellerId), now);
    }

    void applyConfirmed(Long orderId, Long sellerId, LocalDateTime now) {
        apply(orderId, order -> order.applyConfirmed(sellerId), now);
    }

    private void apply(Long orderId, Consumer<PaybackOrder> change, LocalDateTime now) {
        IndividualGroupPurchasePayback payback = orderRepository.findIndividualGroupPurchaseId(orderId)
                .map(groupPurchaseSnapshotUseCase::lock)
                .orElse(null);
        PaybackOrder order = lockOrder(orderId);
        change.accept(order);
        if (payback == null && order.getIndividualGroupPurchaseId() != null) {
            payback = groupPurchaseSnapshotUseCase.lock(order.getIndividualGroupPurchaseId());
        }
        if (payback != null) {
            paybackJudgeUseCase.judgeLocked(payback, now);
        }
    }

    private PaybackOrder lockOrder(Long orderId) {
        orderRepository.insertIfAbsent(orderId);
        return orderRepository.findByIdForUpdate(orderId).orElseThrow();
    }
}
