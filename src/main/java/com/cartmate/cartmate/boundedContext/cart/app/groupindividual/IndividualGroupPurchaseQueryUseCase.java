package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseProduct;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.out.MemberClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseQueryUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final IndividualGroupPurchaseOrderClient orderClient;
    private final MemberClient memberClient;

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
        IndividualGroupPurchase purchase = getWithProductsForCreator(memberId, individualGroupPurchaseId);
        List<IndividualGroupPurchaseOrderLine> lines = orderClient.findOrderLines(individualGroupPurchaseId);
        Map<Long, String> nicknames = memberClient.findNicknames(lines.stream()
                .map(IndividualGroupPurchaseOrderLine::buyerMemberId)
                .collect(Collectors.toSet()));
        return IndividualGroupPurchaseDashboardResponse.of(purchase, productOptionsOf(purchase), lines, nicknames,
                LocalDateTime.now());
    }

    List<IndividualGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        return individualGroupPurchaseRepository.findAllByCreatorMemberIdOrderByIdDesc(memberId).stream()
                .map(purchase -> IndividualGroupPurchaseSummaryResponse.of(purchase, now))
                .toList();
    }

    IndividualGroupPurchaseOrderable getOrderable(Long individualGroupPurchaseId, List<Long> productIds, LocalDateTime now) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findByIdForUpdate(individualGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
        productIds.stream()
                .distinct()
                .forEach(productId -> purchase.validateOrderable(productId, now));
        return new IndividualGroupPurchaseOrderable(purchase.getId(), purchase.getDeadlineAt());
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
        return IndividualGroupPurchaseDetailResponse.of(purchase, productOptionsOf(purchase), viewerId, LocalDateTime.now());
    }

    private Map<Long, CartProductOptions> productOptionsOf(IndividualGroupPurchase purchase) {
        List<Long> productIds = purchase.getProducts().stream()
                .map(IndividualGroupPurchaseProduct::getProductId)
                .toList();
        return individualGroupPurchaseSupport.productOptionsOf(productIds);
    }
}
