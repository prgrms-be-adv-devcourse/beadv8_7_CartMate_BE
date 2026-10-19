package com.cartmate.cartmate.boundedContext.order.dto.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderItem;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderParticipantStatus;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SharedGroupOrderDetailResponse(
        Long id,
        Long sharedGroupPurchaseId,
        SharedGroupOrderStatus status,
        SharedGroupOrderCancelReason cancelReason,
        boolean creator,
        long totalAmount,
        long myShareAmount,
        SharedGroupOrderParticipantStatus myPaymentStatus,
        LocalDateTime paymentDeadlineAt,
        LocalDateTime confirmedAt,
        LocalDateTime canceledAt,
        AddressResponse teamDeliveryAddress,
        List<ParticipantResponse> participants,
        List<ItemResponse> items,
        LocalDateTime serverTime
) {

    public static SharedGroupOrderDetailResponse of(
            SharedGroupOrder order, Map<Long, OrderProductInfo> products, Long viewerId, LocalDateTime now
    ) {
        SharedGroupOrderParticipant me = order.getParticipant(viewerId);
        List<ParticipantResponse> participants = order.getParticipants().stream()
                .map(participant -> ParticipantResponse.of(participant, order.isCreator(participant.getMemberId())))
                .toList();
        List<ItemResponse> items = order.getItems().stream()
                .map(item -> ItemResponse.of(item, products.get(item.getInventoryId())))
                .toList();
        return new SharedGroupOrderDetailResponse(
                order.getId(),
                order.getSharedGroupPurchaseId(),
                order.getStatus(),
                order.getCancelReason(),
                order.isCreator(viewerId),
                order.getTotalAmount(),
                me.getShareAmount(),
                me.getStatus(),
                order.getPaymentDeadlineAt(),
                order.getConfirmedAt(),
                order.getCanceledAt(),
                AddressResponse.from(order.getTeamDeliveryAddress()),
                participants,
                items,
                now
        );
    }

    public record ParticipantResponse(
            Long memberId,
            boolean creator,
            long shareAmount,
            SharedGroupOrderParticipantStatus status,
            LocalDateTime paidAt,
            long refundedAmount
    ) {

        static ParticipantResponse of(SharedGroupOrderParticipant participant, boolean creator) {
            return new ParticipantResponse(
                    participant.getMemberId(),
                    creator,
                    participant.getShareAmount(),
                    participant.getStatus(),
                    participant.getPaidAt(),
                    participant.getRefundedAmount()
            );
        }
    }

    public record ItemResponse(
            Long itemId,
            Long productId,
            Long inventoryId,
            String productName,
            String optionName,
            String imageUrl,
            int quantity,
            long unitPrice,
            long amount,
            boolean refunded
    ) {

        static ItemResponse of(SharedGroupOrderItem item, OrderProductInfo product) {
            return new ItemResponse(
                    item.getId(),
                    item.getProductId(),
                    item.getInventoryId(),
                    product == null ? null : product.productName(),
                    product == null ? null : product.optionName(),
                    product == null ? null : product.imageUrl(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.amount(),
                    item.isRefunded()
            );
        }
    }

    public record AddressResponse(
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail
    ) {

        static AddressResponse from(DeliveryAddress address) {
            if (address == null) {
                return null;
            }
            return new AddressResponse(
                    address.getReceiverName(),
                    address.getReceiverPhone(),
                    address.getZipCode(),
                    address.getAddress(),
                    address.getAddressDetail()
            );
        }
    }
}
