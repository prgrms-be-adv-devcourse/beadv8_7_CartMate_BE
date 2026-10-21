package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerDeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItemOption;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemOptionRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryQueryUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryItemRepository deliveryItemRepository;
    private final DeliveryItemOptionRepository deliveryItemOptionRepository;
    private final SellerQuery sellerQuery;

    public Page<DeliveryResponse> getMyDeliveries(Long memberId, Pageable pageable) {
        return deliveryRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, pageable)
                .map(DeliveryResponse::from);
    }

    // 구매자 본인의 한 주문 배송 목록. 주문은 판매자별로 배송이 나뉘므로 여러 건일 수 있다.
    public List<DeliveryResponse> getMyDeliveriesByOrder(Long memberId, Long orderId, PurchaseType purchaseType) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        return deliveryRepository
                .findByMemberIdAndOrderIdAndPurchaseTypeOrderByCreatedAtDescIdDesc(memberId, orderId, purchaseType)
                .stream()
                .map(DeliveryResponse::from)
                .toList();
    }

    public DeliveryDetailResponse getDeliveryDetails(Long deliveryId, Long memberId) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        // 수령인이 아니면 해당 배송의 판매자인지 확인한다.
        boolean buyer = delivery.getMemberId().equals(memberId);

        if (!buyer && !sellerQuery.isSellerOwner(memberId, delivery.getSellerId())) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        List<DeliveryItem> items = deliveryItemRepository.findByDeliveryId(deliveryId);

        return DeliveryDetailResponse.from(delivery, items, findOptionsByItemId(items));
    }

    // 로그인한 판매자의 배송 목록을 상태별로 최신순 조회한다. 아이템과 옵션은 페이지 단위로 한 번에 가져온다.
    public Page<SellerDeliveryResponse> getSellerDeliveries(Long memberId, DeliveryStatus status, Pageable pageable) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        Long sellerId = sellerQuery.findSellerIdByMemberId(memberId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_SELLER_ONLY));

        Page<Delivery> deliveries = deliveryRepository
                .findBySellerIdAndStatusOrderByCreatedAtDescIdDesc(sellerId, status, pageable);
        if (deliveries.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, deliveries.getTotalElements());
        }

        List<Long> deliveryIds = deliveries.getContent().stream().map(Delivery::getId).toList();
        List<DeliveryItem> items = deliveryItemRepository.findByDeliveryIdIn(deliveryIds);
        Map<Long, List<DeliveryItem>> itemsByDeliveryId = items.stream()
                .collect(Collectors.groupingBy(item -> item.getDelivery().getId()));
        Map<Long, List<DeliveryItemOption>> optionsByItemId = findOptionsByItemId(items);

        return deliveries.map(delivery -> SellerDeliveryResponse.from(
                delivery, itemsByDeliveryId.getOrDefault(delivery.getId(), List.of()), optionsByItemId));
    }

    private Map<Long, List<DeliveryItemOption>> findOptionsByItemId(List<DeliveryItem> items) {
        List<Long> itemIds = items.stream().map(DeliveryItem::getId).toList();
        return deliveryItemOptionRepository
                .findByDeliveryItemIdInOrderByDeliveryItemIdAscSortOrderAsc(itemIds)
                .stream()
                .collect(Collectors.groupingBy(option -> option.getDeliveryItem().getId()));
    }
}
