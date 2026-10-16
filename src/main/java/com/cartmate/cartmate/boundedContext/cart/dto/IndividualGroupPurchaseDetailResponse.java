package com.cartmate.cartmate.boundedContext.cart.dto;

import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.domain.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.IndividualGroupPurchaseStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


public record IndividualGroupPurchaseDetailResponse(
        Long id,
        String title,
        IndividualGroupPurchaseStatus status,
        String inviteCode,
        boolean viewerIsCreator,
        boolean open,
        LocalDateTime deadlineAt,
        LocalDateTime closedAt,
        LocalDateTime serverTime,
        List<ProductResponse> products
) {


    public static IndividualGroupPurchaseDetailResponse of(IndividualGroupPurchase purchase,
                                                           Map<Long, CartProductOptions> productOptions,
                                                           Long viewerId,
                                                           LocalDateTime now) {
        List<ProductResponse> products = purchase.getProducts().stream()
                .map(product -> ProductResponse.of(product.getProductId(), productOptions.get(product.getProductId())))
                .toList();
        return new IndividualGroupPurchaseDetailResponse(
                purchase.getId(),
                purchase.getTitle(),
                purchase.getStatus(),
                purchase.getInviteCode(),
                purchase.isCreator(viewerId),
                purchase.isOpen(now),
                purchase.getDeadlineAt(),
                purchase.getClosedAt(),
                now,
                products
        );
    }

    public record ProductResponse(
            Long productId,
            String productName,
            String imageUrl,
            boolean purchasable,
            List<CartProductOptions.Option> options
    ) {

        static ProductResponse of(Long productId, CartProductOptions info) {
            if (info == null) {
                return new ProductResponse(productId, null, null, false, List.of());
            }
            return new ProductResponse(info.productId(), info.productName(), info.imageUrl(),
                    info.purchasable(), info.options());
        }
    }
}
