package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchaseItem;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.AbuseRestrictionClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.orderService.shared.abuse.dto.AbuseRestrictionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseSupport {

    private static final DateTimeFormatter RESTRICTED_UNTIL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final ProductClient productClient;
    private final AbuseRestrictionClient abuseRestrictionClient;

    SharedGroupPurchase getPurchaseForUpdate(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseRepository.findByIdForUpdate(sharedGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
    }

    void validateNotRestricted(Long memberId) {
        AbuseRestrictionStatus restriction = abuseRestrictionClient.getRestriction(memberId);
        if (!restriction.restricted()) {
            return;
        }
        CartErrorCode errorCode = CartErrorCode.CART_MEMBER_RESTRICTED;
        throw new ServiceException(errorCode.resultCode(),
                errorCode.getMsg() + " 제한 종료: " + restriction.restrictedUntil().format(RESTRICTED_UNTIL_FORMAT));
    }

    Set<Long> findUnavailableInventoryIds(SharedGroupPurchase purchase) {
        return productClient.findUnavailableInventoryIds(inventoryIdsOf(purchase));
    }

    List<Long> inventoryIdsOf(SharedGroupPurchase purchase) {
        return purchase.getItems().stream()
                .map(SharedGroupPurchaseItem::getInventoryId)
                .toList();
    }
}
