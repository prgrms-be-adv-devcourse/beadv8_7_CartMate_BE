package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class ProductFacadeProductClient implements ProductClient {

    private final ProductFacade productFacade;

    @Override
    public Optional<CartProductInfo> findByInventoryId(Long inventoryId) {
        return findAllByInventoryIds(List.of(inventoryId)).stream().findFirst();
    }

    @Override
    public List<CartProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        return productFacade.findProductsForPurchase(inventoryIds).stream()
                .map(this::toCartProductInfo)
                .toList();
    }

    @Override
    public List<CartProductOptions> findAllProductOptions(Collection<Long> productIds) {
        return productFacade.findProductsForPurchase(productIds).stream()
                .map(this::toCartProductOptions)
                .toList();
    }

    private CartProductInfo toCartProductInfo(ProductForPurchase product) {
        return new CartProductInfo(
                product.productId(),
                product.productId(),
                product.name(),
                null,
                Math.toIntExact(product.basePrice()),
                product.imageUrl(),
                product.purchasable()
        );
    }

    private CartProductOptions toCartProductOptions(ProductForPurchase product) {
        return new CartProductOptions(
                product.productId(),
                product.name(),
                product.imageUrl(),
                product.purchasable(),
                List.of(new CartProductOptions.Option(
                        product.productId(),
                        null,
                        Math.toIntExact(product.basePrice()),
                        product.purchasable()
                ))
        );
    }
}
