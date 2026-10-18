package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
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
public class DeliveryClaimQueryUseCase {

    private final DeliveryClaimRepository deliveryClaimRepository;
    private final DeliveryClaimItemRepository deliveryClaimItemRepository;

    // 내가 접수한 교환·환불을 최신순으로 조회한다. 상품은 페이지 단위로 한 번에 가져온다.
    public Page<DeliveryClaimResponse> getMyClaims(Long memberId, Pageable pageable) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }

        Page<DeliveryClaim> claims = deliveryClaimRepository
                .findByMemberIdOrderByCreatedAtDescIdDesc(memberId, pageable);
        if (claims.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, claims.getTotalElements());
        }

        List<Long> claimIds = claims.getContent().stream().map(DeliveryClaim::getId).toList();
        Map<Long, List<DeliveryClaimItem>> itemsByClaimId = deliveryClaimItemRepository
                .findAllWithDeliveryItemByClaimIds(claimIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getClaim().getId()));

        return claims.map(claim -> DeliveryClaimResponse.from(
                claim, itemsByClaimId.getOrDefault(claim.getId(), List.of())));
    }
}
