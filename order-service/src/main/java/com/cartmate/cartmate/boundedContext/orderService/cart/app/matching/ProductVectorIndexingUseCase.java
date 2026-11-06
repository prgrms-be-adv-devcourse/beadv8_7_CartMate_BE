package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.EmbeddingText;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductToIndex;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductEmbeddingClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductVectorIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class ProductVectorIndexingUseCase {

    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final ProductEmbeddingClient productEmbeddingClient;
    private final ProductVectorIndex productVectorIndex;

    int index(List<ProductToIndex> products) {
        if (products.isEmpty()) {
            return 0;
        }
        List<EmbeddingText> texts = embeddingTextBuilder.build(products);
        ProductEmbeddingClient.Embeddings embeddings = productEmbeddingClient.embed(texts.stream().map(EmbeddingText::text).toList());
        if (texts.size() != products.size() || embeddings.vectors().size() != products.size()) {
            throw new IllegalStateException("embedding count mismatch: products=" + products.size()
                    + ", texts=" + texts.size() + ", vectors=" + embeddings.vectors().size());
        }
        List<ProductVector> vectors = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            ProductToIndex product = products.get(i);
            vectors.add(new ProductVector(product.productId(), product.categoryId(), embeddings.model(),
                    texts.get(i).version(), embeddings.vectors().get(i)));
        }
        productVectorIndex.saveAll(vectors);
        return vectors.size();
    }
}
