package com.cartmate.cartmate.boundedContext.cart.app.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.DemandCandidate;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductDemand;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.SimilarProductDemand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class DemandAggregator {

    private static final Comparator<DemandCandidate> MOST_DEMANDED_FIRST =
            Comparator.comparingInt(DemandCandidate::totalMemberCount).reversed()
                    .thenComparing(Comparator.comparingInt(DemandCandidate::exactMemberCount).reversed())
                    .thenComparing(DemandCandidate::productId);

    private final ProductSimilarityResolver productSimilarityResolver;

    List<DemandCandidate> aggregate(Collection<ProductDemand> demands, int minMemberCount) {
        if (minMemberCount < 1) {
            throw new IllegalArgumentException("minMemberCount must be at least 1");
        }
        Map<Long, Set<Long>> memberIdsByProductId = demands.stream()
                .filter(demand -> !demand.memberIds().isEmpty())
                .collect(Collectors.toMap(ProductDemand::productId, demand -> new HashSet<>(demand.memberIds()),
                        (current, next) -> {
                            current.addAll(next);
                            return current;
                        }));

        Map<Long, List<ProductSimilarity>> similaritiesByProductId =
                productSimilarityResolver.resolveAll(memberIdsByProductId.keySet(), memberIdsByProductId.keySet());

        return memberIdsByProductId.keySet().stream()
                .map(productId -> toCandidate(productId,
                        similaritiesByProductId.getOrDefault(productId, List.of()), memberIdsByProductId))
                .filter(candidate -> candidate.totalMemberCount() >= minMemberCount)
                .sorted(MOST_DEMANDED_FIRST)
                .toList();
    }

    private DemandCandidate toCandidate(Long productId, List<ProductSimilarity> similarities,
                                        Map<Long, Set<Long>> memberIdsByProductId) {
        List<ProductSimilarity> others = similarities.stream()
                .filter(similarity -> !Objects.equals(similarity.productId(), productId))
                .toList();

        Set<Long> exactMemberIds = memberIdsByProductId.get(productId);
        Set<Long> similarMemberIds = others.stream()
                .flatMap(similarity -> memberIdsByProductId.get(similarity.productId()).stream())
                .filter(memberId -> !exactMemberIds.contains(memberId))
                .collect(Collectors.toSet());

        List<SimilarProductDemand> similarProducts = others.stream()
                .map(similarity -> new SimilarProductDemand(similarity.productId(), similarity.similarity(),
                        similarity.matchType(), memberIdsByProductId.get(similarity.productId()).size()))
                .toList();

        return new DemandCandidate(productId, exactMemberIds.size(), similarMemberIds.size(), similarProducts);
    }
}
