package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseSupport {

    private final ProductClient productClient;

    void validatePurchasableProducts(Collection<Long> productIds) {
        Map<Long, CartProductOptions> found = productOptionsOf(productIds);
        for (Long productId : productIds.stream().distinct().toList()) {
            CartProductOptions info = found.get(productId);
            if (info == null) {
                throw new ServiceException(CartErrorCode.CART_PRODUCT_NOT_FOUND);
            }
            if (!info.purchasable()) {
                throw new ServiceException(CartErrorCode.CART_PRODUCT_NOT_ON_SALE);
            }
        }
    }

    Map<Long, CartProductOptions> productOptionsOf(Collection<Long> productIds) {
        return productClient.findAllProductOptions(productIds).stream()
                .collect(Collectors.toMap(CartProductOptions::productId, Function.identity()));
    }
}
