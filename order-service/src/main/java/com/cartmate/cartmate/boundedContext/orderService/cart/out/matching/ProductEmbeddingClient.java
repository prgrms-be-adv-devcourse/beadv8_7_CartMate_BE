package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import java.util.List;

public interface ProductEmbeddingClient {

    Embeddings embed(List<String> texts);

    String primaryModel();

    record Embeddings(String model, List<float[]> vectors) {
    }
}
