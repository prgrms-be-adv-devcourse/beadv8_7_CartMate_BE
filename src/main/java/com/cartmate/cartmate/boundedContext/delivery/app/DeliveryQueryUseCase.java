package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryQueryUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryItemRepository deliveryItemRepository;
    private final SellerQuery sellerQuery;

    public Page<DeliveryResponse> getMyDeliveries(Long memberId, Pageable pageable) {
        return deliveryRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, pageable)
                .map(DeliveryResponse::from);
    }

    public DeliveryDetailResponse getDeliveryDetails(Long deliveryId, Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID가 필요합니다.");
        }
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("배송을 찾을 수 없습니다."));

        // 수령인이 아니면 해당 배송의 판매자인지 확인한다.
        boolean buyer = delivery.getMemberId().equals(memberId);

        if (!buyer && !sellerQuery.isSellerOwner(memberId, delivery.getSellerId())) {
            throw new IllegalArgumentException("배송 조회 권한이 없습니다.");
        };

        List<DeliveryItem> items = deliveryItemRepository.findByDeliveryId(deliveryId);

        return DeliveryDetailResponse.from(delivery, items);
    }
}
