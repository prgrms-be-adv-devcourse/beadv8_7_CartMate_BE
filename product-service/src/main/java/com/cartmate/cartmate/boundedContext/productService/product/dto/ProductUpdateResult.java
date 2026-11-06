package com.cartmate.cartmate.boundedContext.productService.product.dto;

import java.util.List;

public record ProductUpdateResult(
        List<SyncProduct> syncProductList,
        List<String> deletedKeys
) {}
