package com.cartmate.cartmate.boundedContext.cart.app.matching;

import com.cartmate.cartmate.boundedContext.cart.dto.matching.DemandCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartMatchingFacade {

    private final DemandCandidateQueryUseCase demandCandidateQueryUseCase;

    @Transactional(readOnly = true)
    public List<DemandCandidateResponse> getDemandCandidates(int minMembers, int days, int size) {
        return demandCandidateQueryUseCase.getCandidates(minMembers, days, size, LocalDateTime.now());
    }
}
