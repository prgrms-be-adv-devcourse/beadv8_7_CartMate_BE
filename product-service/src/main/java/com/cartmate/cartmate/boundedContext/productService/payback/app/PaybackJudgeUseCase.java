package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.IndividualGroupPurchasePayback;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackSettlement;
import com.cartmate.cartmate.boundedContext.productService.payback.out.PaybackOrderRepository;
import com.cartmate.cartmate.shared.cart.GroupPurchaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class PaybackJudgeUseCase {

    private final PaybackOrderRepository orderRepository;
    private final PaybackPayoutUseCase paybackPayoutUseCase;

    void judgeLocked(IndividualGroupPurchasePayback payback, LocalDateTime now) {
        if (!payback.isJudgeable(now)) {
            return;
        }
        Long purchaseId = payback.getIndividualGroupPurchaseId();
        PaybackSettlement.confirmedLines(orderRepository.findAllByIndividualGroupPurchaseId(purchaseId))
                .ifPresent(lines -> {
                    PaybackSettlement.Result result = PaybackSettlement.settle(lines, payback.getConditionSnapshots());
                    payback.judge(result, now);
                    if (result.paid()) {
                        paybackPayoutUseCase.requestAll(GroupPurchaseType.INDIVIDUAL, purchaseId, result.payouts(),
                                now);
                    }
                });
    }
}
