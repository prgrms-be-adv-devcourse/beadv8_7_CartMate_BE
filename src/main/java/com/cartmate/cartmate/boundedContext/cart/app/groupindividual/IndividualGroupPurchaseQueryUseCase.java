package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseQueryUseCase {

    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final IndividualGroupPurchaseOrderClient orderClient;

    IndividualGroupPurchaseDetailResponse getByInviteCode(Long memberId, String inviteCode) {
        IndividualGroupPurchase purchase = individualGroupPurchaseSupport.getWithProductsByInviteCode(inviteCode);
        return toDetailResponse(purchase, memberId);
    }

    IndividualGroupPurchaseDetailResponse getDetail(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseSupport.getWithProductsForCreator(memberId, individualGroupPurchaseId);
        return toDetailResponse(purchase, memberId);
    }

    IndividualGroupPurchaseDashboardResponse getDashboard(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseSupport.getWithProductsForCreator(memberId, individualGroupPurchaseId);
        List<IndividualGroupPurchaseOrderLine> lines = orderClient.findOrderLines(individualGroupPurchaseId);
        return IndividualGroupPurchaseDashboardResponse.of(
                purchase, individualGroupPurchaseSupport.productOptionsOf(purchase), lines, LocalDateTime.now());
    }

    List<IndividualGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        return individualGroupPurchaseSupport.findAllByCreator(memberId).stream()
                .map(purchase -> IndividualGroupPurchaseSummaryResponse.of(purchase, now))
                .toList();
    }

    private IndividualGroupPurchaseDetailResponse toDetailResponse(IndividualGroupPurchase purchase, Long viewerId) {
        return IndividualGroupPurchaseDetailResponse.of(
                purchase, individualGroupPurchaseSupport.productOptionsOf(purchase), viewerId, LocalDateTime.now());
    }
}
