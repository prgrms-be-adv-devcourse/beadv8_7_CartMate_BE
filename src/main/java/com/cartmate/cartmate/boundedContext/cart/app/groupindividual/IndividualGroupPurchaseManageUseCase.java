package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseManageUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final IndividualGroupPurchaseOrderClient orderClient;

    void addProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        individualGroupPurchaseSupport.validatePurchasableProducts(List.of(productId));
        getPurchaseForUpdate(individualGroupPurchaseId)
                .addProduct(memberId, productId, LocalDateTime.now());
    }

    void removeProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        boolean hasOrders = orderClient.hasOrders(individualGroupPurchaseId, productId);
        purchase.removeProduct(memberId, productId, hasOrders, LocalDateTime.now());
    }

    void changeDeadline(Long memberId, Long individualGroupPurchaseId, LocalDateTime deadlineAt) {
        getPurchaseForUpdate(individualGroupPurchaseId)
                .changeDeadline(memberId, deadlineAt, LocalDateTime.now());
    }

    void close(Long memberId, Long individualGroupPurchaseId) {
        getPurchaseForUpdate(individualGroupPurchaseId)
                .close(memberId, LocalDateTime.now());
    }

    private IndividualGroupPurchase getPurchaseForUpdate(Long individualGroupPurchaseId) {
        return individualGroupPurchaseRepository.findByIdForUpdate(individualGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
    }
}
