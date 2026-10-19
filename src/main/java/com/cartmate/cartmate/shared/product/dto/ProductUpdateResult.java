package com.cartmate.cartmate.shared.product.dto;

import java.util.List;

public record ProductUpdateResult(
        List<SyncProduct> syncProductList,
        List<String> deletedKeys
) {}
