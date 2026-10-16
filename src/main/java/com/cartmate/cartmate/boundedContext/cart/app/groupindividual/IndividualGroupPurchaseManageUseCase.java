package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseManageUseCase {

    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final IndividualGroupPurchaseOrderClient orderClient;

    void addProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        individualGroupPurchaseSupport.validatePurchasableProducts(List.of(productId));
        individualGroupPurchaseSupport.getPurchaseForUpdate(individualGroupPurchaseId)
                .addProduct(memberId, productId, LocalDateTime.now());
    }

    void removeProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        IndividualGroupPurchase purchase = individualGroupPurchaseSupport.getPurchaseForUpdate(individualGroupPurchaseId);
        boolean hasOrders = orderClient.hasOrders(individualGroupPurchaseId, productId);
        purchase.removeProduct(memberId, productId, hasOrders, LocalDateTime.now());
    }

    void changeDeadline(Long memberId, Long individualGroupPurchaseId, LocalDateTime deadlineAt) {
        individualGroupPurchaseSupport.getPurchaseForUpdate(individualGroupPurchaseId)
                .changeDeadline(memberId, deadlineAt, LocalDateTime.now());
    }

    void close(Long memberId, Long individualGroupPurchaseId) {
        individualGroupPurchaseSupport.getPurchaseForUpdate(individualGroupPurchaseId)
                .close(memberId, LocalDateTime.now());
    }
}
