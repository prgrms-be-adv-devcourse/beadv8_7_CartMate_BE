package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
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
public class DeliveryClaimQueryUseCase {

    private final DeliveryClaimRepository deliveryClaimRepository;
    private final DeliveryClaimItemRepository deliveryClaimItemRepository;
    private final SellerQuery sellerQuery;

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

        Map<Long, List<DeliveryClaimItem>> itemsByClaimId = findItemsByClaimId(claims);

        return claims.map(claim -> DeliveryClaimResponse.from(
                claim, itemsByClaimId.getOrDefault(claim.getId(), List.of())));
    }

    // 로그인한 판매자의 배송에 접수된 교환·환불을 최신순으로 조회한다.
    // 상태나 유형을 주지 않으면 전체를 조회한다.
    public Page<SellerClaimResponse> getSellerClaims(
            Long memberId, List<ClaimStatus> statuses, ClaimType claimType, Pageable pageable
    ) {
        if (memberId == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }
        Long sellerId = sellerQuery.findSellerIdByMemberId(memberId)
                .orElseThrow(() -> new ServiceException(DeliveryErrorCode.DELIVERY_SELLER_ONLY));

        List<ClaimStatus> statusFilter = statuses == null || statuses.isEmpty()
                ? List.of(ClaimStatus.values()) : statuses;
        List<ClaimType> typeFilter = claimType == null ? List.of(ClaimType.values()) : List.of(claimType);

        Page<DeliveryClaim> claims = deliveryClaimRepository
                .findSellerClaims(sellerId, statusFilter, typeFilter, pageable);
        if (claims.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, claims.getTotalElements());
        }

        Map<Long, List<DeliveryClaimItem>> itemsByClaimId = findItemsByClaimId(claims);

        return claims.map(claim -> SellerClaimResponse.from(
                claim, itemsByClaimId.getOrDefault(claim.getId(), List.of())));
    }

    private Map<Long, List<DeliveryClaimItem>> findItemsByClaimId(Page<DeliveryClaim> claims) {
        List<Long> claimIds = claims.getContent().stream().map(DeliveryClaim::getId).toList();
        return deliveryClaimItemRepository.findAllWithDeliveryItemByClaimIds(claimIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getClaim().getId()));
    }
}
