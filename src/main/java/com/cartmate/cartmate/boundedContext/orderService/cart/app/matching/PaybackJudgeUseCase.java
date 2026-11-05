package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplica;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.PaybackJudgment;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.PaybackOrderOutcome;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.PaybackOrderOutcomeType;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.PaybackSettlement;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseOrderReplicaRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.PaybackJudgmentRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.PaybackOrderOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class PaybackJudgeUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final IndividualGroupPurchaseOrderReplicaRepository orderReplicaRepository;
    private final PaybackOrderOutcomeRepository paybackOrderOutcomeRepository;
    private final PaybackJudgmentRepository paybackJudgmentRepository;

    void recordConfirmed(Long orderId, LocalDateTime confirmedAt, LocalDateTime now) {
        record(PaybackOrderOutcome.confirmed(orderId, confirmedAt), now);
    }

    void recordCanceled(Long orderId, LocalDateTime canceledAt, LocalDateTime now) {
        record(PaybackOrderOutcome.canceled(orderId, canceledAt), now);
    }

    void recordSellerItemsCanceled(Long orderId, Long sellerId, LocalDateTime canceledAt, LocalDateTime now) {
        boolean wholeOrderCanceled = orderReplicaRepository.findById(orderId)
                .map(replica -> !replica.getLines().isEmpty() && replica.getLines().stream()
                        .allMatch(line -> sellerId != null && sellerId.equals(line.getSellerId())))
                .orElse(false);
        if (wholeOrderCanceled) {
            recordCanceled(orderId, canceledAt, now);
        }
    }

    void judge(Long individualGroupPurchaseId, LocalDateTime now) {
        lockDemandPurchase(individualGroupPurchaseId).ifPresent(purchase -> judgeLocked(purchase, now));
    }

    private void record(PaybackOrderOutcome outcome, LocalDateTime now) {
        Optional<Long> purchaseId = orderReplicaRepository.findById(outcome.getOrderId())
                .map(IndividualGroupPurchaseOrderReplica::getIndividualGroupPurchaseId);
        Optional<IndividualGroupPurchase> lockedPurchase = purchaseId.flatMap(this::lockDemandPurchase);
        if (purchaseId.isPresent() && lockedPurchase.isEmpty()) {
            return;
        }
        if (!paybackOrderOutcomeRepository.existsById(outcome.getOrderId())) {
            paybackOrderOutcomeRepository.save(outcome);
        }
        lockedPurchase.ifPresent(purchase -> judgeLocked(purchase, now));
    }

    private Optional<IndividualGroupPurchase> lockDemandPurchase(Long individualGroupPurchaseId) {
        if (individualGroupPurchaseId == null) {
            return Optional.empty();
        }
        return individualGroupPurchaseRepository.findByIdForUpdate(individualGroupPurchaseId)
                .filter(IndividualGroupPurchase::isFromDemand);
    }

    private void judgeLocked(IndividualGroupPurchase purchase, LocalDateTime now) {
        Long purchaseId = purchase.getId();
        if (purchase.isOpen(now) || paybackJudgmentRepository.existsById(purchaseId)) {
            return;
        }

        List<PaybackSettlement.Order> orders = orderReplicaRepository.findAllByIndividualGroupPurchaseId(purchaseId)
                .stream()
                .map(replica -> new PaybackSettlement.Order(
                        replica.getOrderId(), replica.getBuyerMemberId(), replica.getStatus()))
                .toList();
        Map<Long, PaybackOrderOutcomeType> outcomes = paybackOrderOutcomeRepository
                .findAllById(orders.stream().map(PaybackSettlement.Order::orderId).toList()).stream()
                .collect(Collectors.toMap(PaybackOrderOutcome::getOrderId, PaybackOrderOutcome::getOutcome));

        PaybackSettlement.confirmedBuyerCount(orders, outcomes).ifPresent(buyerCount ->
                paybackJudgmentRepository.save(PaybackJudgment.judge(purchaseId,
                        purchase.getPaybackMinBuyers(), purchase.getPaybackAmount(), buyerCount, now)));
    }
}
