package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.app.InviteCodeGenerator;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseProduct;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndividualGroupPurchaseService {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final ProductClient productClient;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final IndividualGroupPurchaseOrderClient orderClient;


    @Transactional
    public IndividualGroupPurchaseCreateResponse create(Long memberId, IndividualGroupPurchaseCreateRequest request) {
        validatePurchasableProducts(request.productIds());
        String inviteCode = inviteCodeGenerator.generateUnique(individualGroupPurchaseRepository::existsByInviteCode);
        IndividualGroupPurchase purchase = IndividualGroupPurchase.create(
                request.title(), memberId, inviteCode, request.productIds(), now());
        IndividualGroupPurchase saved = individualGroupPurchaseRepository.save(purchase);
        return IndividualGroupPurchaseCreateResponse.from(saved);
    }


    @Transactional
    public void addProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        validatePurchasableProducts(List.of(productId));
        getPurchaseForUpdate(individualGroupPurchaseId).addProduct(memberId, productId, now());
    }


    @Transactional
    public void removeProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        boolean hasOrders = orderClient.hasOrders(individualGroupPurchaseId, productId);
        purchase.removeProduct(memberId, productId, hasOrders, now());
    }


    @Transactional
    public void changeDeadline(Long memberId, Long individualGroupPurchaseId, LocalDateTime deadlineAt) {
        getPurchaseForUpdate(individualGroupPurchaseId).changeDeadline(memberId, deadlineAt, now());
    }

    @Transactional
    public void close(Long memberId, Long individualGroupPurchaseId) {
        getPurchaseForUpdate(individualGroupPurchaseId).close(memberId, now());
    }


    public IndividualGroupPurchaseDetailResponse getByInviteCode(Long memberId, String inviteCode) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findWithProductsByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));
        return toDetailResponse(purchase, memberId);
    }


    public IndividualGroupPurchaseDetailResponse getDetail(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findWithProductsById(individualGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
        if (!purchase.isCreator(memberId)) {
            throw new IllegalArgumentException("공동구매 생성자만 볼 수 있습니다.");
        }
        return toDetailResponse(purchase, memberId);
    }


    public IndividualGroupPurchaseDashboardResponse getDashboard(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findWithProductsById(individualGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
        if (!purchase.isCreator(memberId)) {
            throw new IllegalArgumentException("공동구매 생성자만 볼 수 있습니다.");
        }
        List<IndividualGroupPurchaseOrderLine> lines = orderClient.findOrderLines(individualGroupPurchaseId);
        List<Long> productIds = purchase.getProducts().stream()
                .map(IndividualGroupPurchaseProduct::getProductId)
                .toList();
        return IndividualGroupPurchaseDashboardResponse.of(purchase, productOptionsOf(productIds), lines, now());
    }

    public List<IndividualGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = now();
        return individualGroupPurchaseRepository.findAllByCreatorMemberIdOrderByIdDesc(memberId).stream()
                .map(purchase -> IndividualGroupPurchaseSummaryResponse.of(purchase, now))
                .toList();
    }


    private IndividualGroupPurchase getPurchaseForUpdate(Long individualGroupPurchaseId) {
        return individualGroupPurchaseRepository.findByIdForUpdate(individualGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
    }


    private void validatePurchasableProducts(Collection<Long> productIds) {
        Map<Long, CartProductOptions> found = productOptionsOf(productIds);
        for (Long productId : productIds.stream().distinct().toList()) {
            CartProductOptions info = found.get(productId);
            if (info == null) {
                throw new IllegalArgumentException("존재하지 않는 상품입니다. productId=" + productId);
            }
            if (!info.purchasable()) {
                throw new IllegalArgumentException("판매 중이 아닌 상품입니다. productId=" + productId);
            }
        }
    }

    private IndividualGroupPurchaseDetailResponse toDetailResponse(IndividualGroupPurchase purchase, Long viewerId) {
        List<Long> productIds = purchase.getProducts().stream()
                .map(IndividualGroupPurchaseProduct::getProductId)
                .toList();
        return IndividualGroupPurchaseDetailResponse.of(purchase, productOptionsOf(productIds), viewerId, now());
    }

    private Map<Long, CartProductOptions> productOptionsOf(Collection<Long> productIds) {
        return productClient.findAllProductOptions(productIds).stream()
                .collect(Collectors.toMap(CartProductOptions::productId, Function.identity()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
