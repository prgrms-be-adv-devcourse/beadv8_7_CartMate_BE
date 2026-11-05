package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.boundedContext.productService.shared.product.dto.ProductForPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
class ProductFacadeOrderProductClient implements OrderProductClient {

    private final ProductFacade productFacade;

    @Override
    public List<OrderProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        return productFacade.findProductsForPurchase(inventoryIds).stream()
                .map(this::toOrderProductInfo)
                .toList();
    }

    private OrderProductInfo toOrderProductInfo(ProductForPurchase product) {
        return new OrderProductInfo(
                product.productId(),
                product.productId(),
                product.sellerId(),
                product.name(),
                null,
                product.basePrice(),
                product.imageUrl(),
                product.purchasable()
        );
    }
}
