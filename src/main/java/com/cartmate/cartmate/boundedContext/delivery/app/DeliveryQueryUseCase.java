package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItemOption;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemOptionRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

        List<Long> itemIds = items.stream().map(DeliveryItem::getId).toList();
        Map<Long, List<DeliveryItemOption>> optionsByItemId = deliveryItemOptionRepository
                .findByDeliveryItemIdInOrderByDeliveryItemIdAscSortOrderAsc(itemIds)
                .stream()
                .collect(Collectors.groupingBy(option -> option.getDeliveryItem().getId()));

        return DeliveryDetailResponse.from(delivery, items, optionsByItemId);
    }
}
