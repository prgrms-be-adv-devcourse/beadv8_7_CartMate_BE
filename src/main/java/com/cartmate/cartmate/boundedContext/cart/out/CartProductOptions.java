package com.cartmate.cartmate.boundedContext.cart.out;

import java.util.List;


public record CartProductOptions(
        Long productId,
        String productName,
        String imageUrl,
        boolean purchasable,
        List<Option> options
) {

    public record Option(
            Long inventoryId,
            String optionName,
            int price,
            boolean purchasable
    ) {
    }
}
