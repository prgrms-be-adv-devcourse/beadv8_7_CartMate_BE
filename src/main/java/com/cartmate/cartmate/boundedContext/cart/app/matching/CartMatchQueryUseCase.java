package com.cartmate.cartmate.boundedContext.cart.app.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.cart.dto.matching.CartMatchResponse;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CartMatchQueryUseCase {

    private final CartRepository cartRepository;
    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final ProductSimilarityResolver productSimilarityResolver;
    private final ProductClient productClient;

    List<CartMatchResponse> getMatches(Long memberId, LocalDateTime now) {
        Set<Long> cartProductIds = cartRepository.findWithItemsByMemberId(memberId)
                .map(cart -> cart.getItems().stream()
                        .map(CartItem::getProductId)
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);
        if (cartProductIds.isEmpty()) {
            return List.of();
        }
        List<IndividualGroupPurchase> openPurchases = individualGroupPurchaseRepository.findOpenDemandPurchases(now);
        if (openPurchases.isEmpty()) {
            return List.of();
        }

        Map<Long, IndividualGroupPurchase> purchaseByProductId = openPurchases.stream()
                .collect(Collectors.toMap(IndividualGroupPurchase::getSourceProductId, Function.identity(),
                        (first, second) -> first));
        Map<Long, List<ProductSimilarity>> similaritiesByCartProductId =
                productSimilarityResolver.resolveAll(cartProductIds, purchaseByProductId.keySet());

        Map<Long, Match> matchByPurchaseProductId = new HashMap<>();
        similaritiesByCartProductId.forEach((cartProductId, similarities) -> similarities.forEach(similarity ->
                matchByPurchaseProductId
                        .computeIfAbsent(similarity.productId(), productId -> new Match(similarity))
                        .add(cartProductId, similarity)));
        if (matchByPurchaseProductId.isEmpty()) {
            return List.of();
        }

        Map<Long, CartProductOptions> products = productClient
                .findAllProductOptions(matchByPurchaseProductId.keySet()).stream()
                .collect(Collectors.toMap(CartProductOptions::productId, Function.identity(), (first, second) -> first));
        return matchByPurchaseProductId.entrySet().stream()
                .filter(entry -> isPurchasable(products.get(entry.getKey())))
                .map(entry -> {
                    Match match = entry.getValue();
                    return CartMatchResponse.of(purchaseByProductId.get(entry.getKey()), products.get(entry.getKey()),
                            match.best.similarity(), match.best.matchType(), new ArrayList<>(match.cartProductIds));
                })
                .sorted(Comparator.comparingDouble(CartMatchResponse::similarity).reversed()
                        .thenComparing(CartMatchResponse::deadlineAt)
                        .thenComparing(CartMatchResponse::individualGroupPurchaseId))
                .toList();
    }

    private static boolean isPurchasable(CartProductOptions product) {
        return product != null && product.purchasable();
    }

    private static final class Match {

        private ProductSimilarity best;
        private final Set<Long> cartProductIds = new TreeSet<>();

        private Match(ProductSimilarity first) {
            this.best = first;
        }

        private void add(Long cartProductId, ProductSimilarity similarity) {
            if (similarity.isStrongerThan(best)) {
                best = similarity;
            }
            cartProductIds.add(cartProductId);
        }
    }
}
