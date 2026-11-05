package com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.DemandCandidate;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.SimilarProductDemand;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductOptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DemandCandidateResponse(
        Long productId,
        String productName,
        String imageUrl,
        boolean purchasable,
        Long openGroupPurchaseId,
        int exactMemberCount,
        int similarMemberCount,
        int totalMemberCount,
        List<SimilarProduct> similarProducts
) {

    public static DemandCandidateResponse of(DemandCandidate candidate, CartProductOptions product,
                                             Long openGroupPurchaseId) {
        List<SimilarProduct> similarProducts = candidate.similarProducts().stream()
                .map(SimilarProduct::from)
                .toList();
        return new DemandCandidateResponse(
                candidate.productId(),
                product == null ? null : product.productName(),
                product == null ? null : product.imageUrl(),
                product != null && product.purchasable(),
                openGroupPurchaseId,
                candidate.exactMemberCount(),
                candidate.similarMemberCount(),
                candidate.totalMemberCount(),
                similarProducts
        );
    }

    @Schema(name = "DemandSimilarProductResponse")
    public record SimilarProduct(
            Long productId,
            double similarity,
            MatchType matchType,
            int memberCount
    ) {

        static SimilarProduct from(SimilarProductDemand similarProduct) {
            return new SimilarProduct(
                    similarProduct.productId(),
                    similarProduct.similarity(),
                    similarProduct.matchType(),
                    similarProduct.memberCount()
            );
        }
    }
}
