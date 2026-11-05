package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.CartMatchResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartMatchingFacade {

    private final DemandCandidateQueryUseCase demandCandidateQueryUseCase;
    private final CartMatchQueryUseCase cartMatchQueryUseCase;
    private final PaybackJudgeUseCase paybackJudgeUseCase;

    @Transactional(readOnly = true)
    public List<DemandCandidateResponse> getDemandCandidates(int minMembers, int days, int size, int cooldownDays) {
        return demandCandidateQueryUseCase.getCandidates(minMembers, days, size, cooldownDays, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<CartMatchResponse> getMyMatches(Long memberId) {
        return cartMatchQueryUseCase.getMatches(memberId, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void recordPaybackOrderConfirmed(Long orderId, LocalDateTime confirmedAt) {
        paybackJudgeUseCase.recordConfirmed(orderId, confirmedAt, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void recordPaybackOrderCanceled(Long orderId, LocalDateTime canceledAt) {
        paybackJudgeUseCase.recordCanceled(orderId, canceledAt, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void recordPaybackSellerItemsCanceled(Long orderId, Long sellerId, LocalDateTime canceledAt) {
        paybackJudgeUseCase.recordSellerItemsCanceled(orderId, sellerId, canceledAt, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void judgePayback(Long individualGroupPurchaseId) {
        paybackJudgeUseCase.judge(individualGroupPurchaseId, LocalDateTime.now());
    }
}
