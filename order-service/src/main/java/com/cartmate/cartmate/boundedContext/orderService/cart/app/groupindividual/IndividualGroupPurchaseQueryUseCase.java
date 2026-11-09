package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseProduct;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseStatus;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.MemberClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseOrderReplicaRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseQueryUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final IndividualGroupPurchaseOrderReplicaRepository orderReplicaRepository;
    private final MemberClient memberClient;
    private final Clock clock;

    IndividualGroupPurchaseDetailResponse getByInviteCode(Long memberId, String inviteCode) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findWithProductsByInviteCode(inviteCode)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_INVALID_INVITE_CODE));
        return toDetailResponse(purchase, memberId);
    }

    IndividualGroupPurchaseDetailResponse getDetail(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = getWithProductsForCreator(memberId, individualGroupPurchaseId);
        return toDetailResponse(purchase, memberId);
    }

    IndividualGroupPurchaseDashboardResponse getDashboard(Long memberId, Long individualGroupPurchaseId) {
        return toDashboard(getWithProductsForCreator(memberId, individualGroupPurchaseId));
    }

    IndividualGroupPurchaseDashboardResponse getDashboardForAdmin(Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findWithProductsById(individualGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
        if (!purchase.isFromDemand()) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_ADMIN_DEMAND_ONLY);
        }
        return toDashboard(purchase);
    }

    private IndividualGroupPurchaseDashboardResponse toDashboard(IndividualGroupPurchase purchase) {
        Long individualGroupPurchaseId = purchase.getId();
        List<IndividualGroupPurchaseOrderLine> lines = orderReplicaRepository.findPaidLines(individualGroupPurchaseId);
        Map<Long, String> nicknames = memberClient.findNicknames(lines.stream()
                .map(IndividualGroupPurchaseOrderLine::buyerMemberId)
                .collect(Collectors.toSet()));
        return IndividualGroupPurchaseDashboardResponse.of(purchase, productOptionsOf(purchase), lines, nicknames,
                LocalDateTime.now(clock));
    }

    List<IndividualGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return individualGroupPurchaseRepository.findAllByCreatorMemberIdOrderByIdDesc(memberId).stream()
                .map(purchase -> IndividualGroupPurchaseSummaryResponse.of(purchase, now))
                .toList();
    }

    Optional<IndividualGroupPurchaseSnapshot> findSnapshot(Long individualGroupPurchaseId) {
        return individualGroupPurchaseRepository.findWithProductsById(individualGroupPurchaseId)
                .map(IndividualGroupPurchaseEvents::snapshot);
    }

    private IndividualGroupPurchase getWithProductsForCreator(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findWithProductsById(individualGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
        if (!purchase.isCreator(memberId)) {
            throw new ServiceException(CartErrorCode.CART_CREATOR_ONLY_VIEW);
        }
        return purchase;
    }

    private IndividualGroupPurchaseDetailResponse toDetailResponse(IndividualGroupPurchase purchase, Long viewerId) {
        return IndividualGroupPurchaseDetailResponse.of(purchase, productOptionsOf(purchase), viewerId, LocalDateTime.now(clock));
    }

    private Map<Long, CartProductOptions> productOptionsOf(IndividualGroupPurchase purchase) {
        List<Long> productIds = purchase.getProducts().stream()
                .map(IndividualGroupPurchaseProduct::getProductId)
                .toList();
        return individualGroupPurchaseSupport.productOptionsOf(productIds);
    }

    long countOpenOfCreator(Long creatorMemberId) {
        return individualGroupPurchaseRepository.countByCreatorMemberIdAndStatusAndDeadlineAtAfter(
                creatorMemberId, IndividualGroupPurchaseStatus.OPEN, LocalDateTime.now(clock));
    }
}
