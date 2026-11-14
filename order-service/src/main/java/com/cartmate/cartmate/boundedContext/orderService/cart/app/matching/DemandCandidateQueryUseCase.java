package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.DemandCandidate;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductDemand;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandCandidateResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.CartDemandReader;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
class DemandCandidateQueryUseCase {

    private static final int MAX_MIN_MEMBERS = 1000;
    private static final int MAX_DAYS = 90;
    private static final int MAX_SIZE = 100;
    private static final int MAX_COOLDOWN_DAYS = 90;
    private static final int DEMAND_POOL_LIMIT = 500;

    private final CartDemandReader cartDemandReader;
    private final DemandAggregator demandAggregator;
    private final ProductClient productClient;
    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;

    List<DemandCandidateResponse> getCandidates(int minExactMembers, int minMembers, int days, int size,
                                                int cooldownDays, LocalDateTime now) {
        validate(minExactMembers, minMembers, days, size, cooldownDays);
        List<Long> coolingDownProductIds = cooldownDays == 0
                ? List.of()
                : individualGroupPurchaseRepository.findDemandProductIdsEndedBetween(now.minusDays(cooldownDays), now);
        List<ProductDemand> demands =
                cartDemandReader.findRecentDemands(now.minusDays(days), 1, DEMAND_POOL_LIMIT, coolingDownProductIds);
        if (demands.size() >= DEMAND_POOL_LIMIT) {
            log.warn("Demand pool reached {} products; less demanded products were left out", DEMAND_POOL_LIMIT);
        }
        List<DemandCandidate> candidates = demandAggregator.aggregate(demands, minExactMembers, minMembers).stream()
                .limit(size)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = candidates.stream().map(DemandCandidate::productId).toList();
        Map<Long, CartProductOptions> products = productClient.findAllProductOptions(productIds).stream()
                .collect(Collectors.toMap(CartProductOptions::productId, Function.identity(), (first, second) -> first));
        Map<Long, Long> openPurchaseIds = individualGroupPurchaseRepository.findOpenDemandPurchaseIds(productIds, now);
        return candidates.stream()
                .map(candidate -> DemandCandidateResponse.of(candidate, products.get(candidate.productId()),
                        openPurchaseIds.get(candidate.productId())))
                .toList();
    }

    private void validate(int minExactMembers, int minMembers, int days, int size, int cooldownDays) {
        if (minMembers < 1 || minMembers > MAX_MIN_MEMBERS) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_MIN_MEMBERS);
        }
        if (minExactMembers < 1 || minExactMembers > minMembers) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_MIN_EXACT_MEMBERS);
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_DAYS);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_SIZE);
        }
        if (cooldownDays < 0 || cooldownDays > MAX_COOLDOWN_DAYS) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_COOLDOWN);
        }
    }
}
