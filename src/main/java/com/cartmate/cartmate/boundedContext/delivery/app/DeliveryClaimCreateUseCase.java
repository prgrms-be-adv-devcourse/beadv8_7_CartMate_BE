package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryClaimCreateUseCase {

    // 판매자가 아직 처리 중이거나 승인되어 진행 중인 클레임. 이 클레임에 포함된 상품은 다시 접수할 수 없다.
    private static final List<ClaimStatus> ACTIVE_STATUSES = List.of(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryItemRepository deliveryItemRepository;
    private final DeliveryClaimRepository deliveryClaimRepository;
    private final DeliveryClaimItemRepository deliveryClaimItemRepository;

    // 수령인(구매자) 본인이 배송완료된 배송에서 상품을 골라 교환·환불을 접수한다. 구매확정 이후에는 접수할 수 없다.
    public DeliveryClaimResponse create(DeliveryClaimCreateCommand command) {
        validate(command);

        // 구매확정과 동시에 실행돼도 순서대로 처리되도록 배송을 잠그고 조회한다. 확정이 먼저면 아래 상태 검사에서 거부된다.
        Delivery delivery = deliveryRepository.findByIdForUpdate(command.deliveryId())
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!delivery.getMemberId().equals(command.memberId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }
        if (delivery.getStatus() != DeliveryStatus.DELIVERED) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS);
        }

        Map<Long, DeliveryItem> deliveryItems = deliveryItemRepository.findByDeliveryId(command.deliveryId())
                .stream()
                .collect(Collectors.toMap(DeliveryItem::getId, Function.identity()));
        validateItems(command.items(), deliveryItems);

        List<Long> requestedItemIds = command.items().stream()
                .map(DeliveryClaimCreateCommand.Item::deliveryItemId)
                .toList();
        if (deliveryClaimItemRepository.existsByDeliveryItemIdsAndClaimStatuses(requestedItemIds, ACTIVE_STATUSES)) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_ALREADY_EXISTS);
        }

        DeliveryClaim claim = DeliveryClaim.create(
                delivery, command.memberId(), command.claimType(), command.reason(), normalize(command.detail()));
        deliveryClaimRepository.save(claim);

        List<DeliveryClaimItem> claimItems = command.items().stream()
                .map(item -> DeliveryClaimItem.create(claim, deliveryItems.get(item.deliveryItemId()), item.quantity()))
                .toList();
        deliveryClaimItemRepository.saveAll(claimItems);

        return DeliveryClaimResponse.from(claim, claimItems);
    }

    private void validate(DeliveryClaimCreateCommand command) {
        if (command.deliveryId() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ID_REQUIRED);
        }
        if (command.memberId() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        if (command.claimType() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_TYPE_REQUIRED);
        }
        if (command.reason() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_REASON_REQUIRED);
        }
        if (command.detail() != null && command.detail().trim().length() > DeliveryClaim.DETAIL_MAX_LENGTH) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_DETAIL_TOO_LONG);
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_ITEMS_REQUIRED);
        }

        Set<Long> seen = new HashSet<>();
        for (DeliveryClaimCreateCommand.Item item : command.items()) {
            if (item == null || item.deliveryItemId() == null) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_ITEM_ID_REQUIRED);
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_QUANTITY_INVALID);
            }
            if (!seen.add(item.deliveryItemId())) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_ITEM_DUPLICATED);
            }
        }
    }

    // 선택한 상품이 모두 이 배송의 상품이고, 요청 수량이 배송 수량을 넘지 않는지 확인한다.
    private void validateItems(List<DeliveryClaimCreateCommand.Item> items, Map<Long, DeliveryItem> deliveryItems) {
        for (DeliveryClaimCreateCommand.Item item : items) {
            DeliveryItem deliveryItem = deliveryItems.get(item.deliveryItemId());
            if (deliveryItem == null) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_ITEM_NOT_FOUND);
            }
            if (item.quantity() > deliveryItem.getQuantity()) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_QUANTITY_EXCEEDED);
            }
        }
    }

    // 앞뒤 공백을 제거하고 비어 있으면 null로 저장한다.
    private String normalize(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return detail.trim();
    }
}
