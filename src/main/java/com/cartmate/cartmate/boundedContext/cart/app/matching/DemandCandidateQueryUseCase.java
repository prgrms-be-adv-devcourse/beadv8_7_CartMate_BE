package com.cartmate.cartmate.boundedContext.cart.app.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.DemandCandidate;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductDemand;
import com.cartmate.cartmate.boundedContext.cart.dto.matching.DemandCandidateResponse;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.matching.CartDemandReader;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DemandCandidateQueryUseCase {

    private static final int MAX_MIN_MEMBERS = 1000;
    private static final int MAX_DAYS = 90;
    private static final int MAX_SIZE = 100;

    private final CartDemandReader cartDemandReader;
    private final DemandAggregator demandAggregator;
    private final ProductClient productClient;

    List<DemandCandidateResponse> getCandidates(int minMembers, int days, int size, LocalDateTime now) {
        validate(minMembers, days, size);
        List<ProductDemand> demands = cartDemandReader.findRecentDemands(now.minusDays(days), minMembers, size);
        List<DemandCandidate> candidates = demandAggregator.aggregate(demands, minMembers).stream()
                .limit(size)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, CartProductOptions> products = productClient.findAllProductOptions(
                        candidates.stream().map(DemandCandidate::productId).toList()).stream()
                .collect(Collectors.toMap(CartProductOptions::productId, Function.identity(), (first, second) -> first));
        return candidates.stream()
                .map(candidate -> DemandCandidateResponse.of(candidate, products.get(candidate.productId())))
                .toList();
    }

    private void validate(int minMembers, int days, int size) {
        if (minMembers < 1 || minMembers > MAX_MIN_MEMBERS) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_MIN_MEMBERS);
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_DAYS);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_SIZE);
        }
    }
}
