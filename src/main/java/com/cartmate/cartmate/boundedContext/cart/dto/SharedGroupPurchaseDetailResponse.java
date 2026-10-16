package com.cartmate.cartmate.boundedContext.cart.dto;

import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.domain.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.SharedGroupPurchaseItem;
import com.cartmate.cartmate.boundedContext.cart.domain.SharedGroupPurchaseMember;
import com.cartmate.cartmate.boundedContext.cart.domain.SharedGroupPurchaseStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


public record SharedGroupPurchaseDetailResponse(
        Long id,
        String title,
        SharedGroupPurchaseStatus status,
        String inviteCode,
        Long creatorMemberId,
        boolean viewerIsCreator,
        int maxParticipants,
        int participantCount,
        boolean editable,
        LocalDateTime editDeadlineAt,
        LocalDateTime frozenAt,
        LocalDateTime closedAt,
        LocalDateTime serverTime,
        List<MemberResponse> members,
        List<ItemResponse> items,
        long totalPrice,
        long perPersonAmount,
        long creatorAmount,
        boolean hasUnavailableItem
) {


    public static SharedGroupPurchaseDetailResponse of(SharedGroupPurchase purchase,
                                                       Map<Long, CartProductInfo> products,
                                                       Long viewerId,
                                                       LocalDateTime now) {
        List<MemberResponse> members = purchase.getMembers().stream()
                .map(member -> MemberResponse.of(member, purchase.isCreator(member.getMemberId())))
                .toList();

        List<ItemResponse> items = purchase.getItems().stream()
                .map(item -> ItemResponse.of(item, products.get(item.getInventoryId())))
                .toList();

        long totalPrice = items.stream()
                .filter(ItemResponse::purchasable)
                .mapToLong(ItemResponse::totalPrice)
                .sum();

        int participantCount = members.size();
        long perPersonAmount = totalPrice / participantCount;
        long remainder = totalPrice % participantCount;

        return new SharedGroupPurchaseDetailResponse(
                purchase.getId(),
                purchase.getTitle(),
                purchase.getStatus(),
                purchase.getInviteCode(),
                purchase.getCreatorMemberId(),
                purchase.isCreator(viewerId),
                purchase.getMaxParticipants(),
                participantCount,
                purchase.isEditable(now),
                purchase.getEditDeadlineAt(),
                purchase.getFrozenAt(),
                purchase.getClosedAt(),
                now,
                members,
                items,
                totalPrice,
                perPersonAmount,
                perPersonAmount + remainder,
                items.stream().anyMatch(item -> !item.purchasable())
        );
    }


    public record MemberResponse(
            Long memberId,
            boolean creator,
            LocalDateTime joinedAt
    ) {
        static MemberResponse of(SharedGroupPurchaseMember member, boolean creator) {
            return new MemberResponse(member.getMemberId(), creator, member.getCreatedAt());
        }
    }


    public record ItemResponse(
            Long itemId,
            Long productId,
            Long inventoryId,
            String productName,
            String optionName,
            String imageUrl,
            int price,
            int quantity,
            long totalPrice,
            boolean purchasable,
            Long updatedMemberId,
            LocalDateTime updatedAt
    ) {

        static ItemResponse of(SharedGroupPurchaseItem item, CartProductInfo product) {
            if (product == null) {
                return new ItemResponse(item.getId(), item.getProductId(), item.getInventoryId(),
                        null, null, null, 0, item.getQuantity(), 0L, false,
                        item.getUpdatedMemberId(), item.getUpdatedAt());
            }
            return new ItemResponse(
                    item.getId(),
                    item.getProductId(),
                    item.getInventoryId(),
                    product.productName(),
                    product.optionName(),
                    product.imageUrl(),
                    product.price(),
                    item.getQuantity(),
                    (long) product.price() * item.getQuantity(),
                    product.purchasable(),
                    item.getUpdatedMemberId(),
                    item.getUpdatedAt()
            );
        }
    }
}
