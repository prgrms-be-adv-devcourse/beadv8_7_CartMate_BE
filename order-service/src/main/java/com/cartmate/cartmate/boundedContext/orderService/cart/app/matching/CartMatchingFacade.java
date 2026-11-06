package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.CartMatchResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartMatchingFacade {

    private final DemandCandidateQueryUseCase demandCandidateQueryUseCase;
    private final CartMatchQueryUseCase cartMatchQueryUseCase;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DemandCandidateResponse> getDemandCandidates(int minMembers, int days, int size, int cooldownDays) {
        return demandCandidateQueryUseCase.getCandidates(minMembers, days, size, cooldownDays, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<CartMatchResponse> getMyMatches(Long memberId) {
        return cartMatchQueryUseCase.getMatches(memberId, LocalDateTime.now(clock));
    }
}
