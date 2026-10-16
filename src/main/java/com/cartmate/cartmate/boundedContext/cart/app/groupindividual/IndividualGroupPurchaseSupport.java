package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseProduct;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseSupport {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final ProductClient productClient;

    IndividualGroupPurchase save(IndividualGroupPurchase purchase) {
        return individualGroupPurchaseRepository.save(purchase);
    }

    boolean existsByInviteCode(String inviteCode) {
        return individualGroupPurchaseRepository.existsByInviteCode(inviteCode);
    }

    IndividualGroupPurchase getPurchaseForUpdate(Long individualGroupPurchaseId) {
        return individualGroupPurchaseRepository.findByIdForUpdate(individualGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
    }

    IndividualGroupPurchase getWithProductsByInviteCode(String inviteCode) {
        return individualGroupPurchaseRepository.findWithProductsByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));
    }

    IndividualGroupPurchase getWithProductsForCreator(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseRepository.findWithProductsById(individualGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
        if (!purchase.isCreator(memberId)) {
            throw new IllegalArgumentException("공동구매 생성자만 볼 수 있습니다.");
        }
        return purchase;
    }

    List<IndividualGroupPurchase> findAllByCreator(Long memberId) {
        return individualGroupPurchaseRepository.findAllByCreatorMemberIdOrderByIdDesc(memberId);
    }

    void validatePurchasableProducts(Collection<Long> productIds) {
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

    Map<Long, CartProductOptions> productOptionsOf(IndividualGroupPurchase purchase) {
        List<Long> productIds = purchase.getProducts().stream()
                .map(IndividualGroupPurchaseProduct::getProductId)
                .toList();
        return productOptionsOf(productIds);
    }

    private Map<Long, CartProductOptions> productOptionsOf(Collection<Long> productIds) {
        return productClient.findAllProductOptions(productIds).stream()
                .collect(Collectors.toMap(CartProductOptions::productId, Function.identity()));
    }
}
