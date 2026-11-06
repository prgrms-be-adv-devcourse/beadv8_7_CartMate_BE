package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.EmbeddingText;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductToIndex;

import java.util.List;

interface EmbeddingTextBuilder {

    List<EmbeddingText> build(List<ProductToIndex> products);
}
