package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.EmbeddingText;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductToIndex;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductProfile;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.CartDemandReader;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductEmbeddingClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductVectorIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
class ProductVectorIndexingUseCase {

    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final ProductEmbeddingClient productEmbeddingClient;
    private final ProductVectorIndex productVectorIndex;
    private final CartDemandReader cartDemandReader;
    private final ProductClient productClient;

    static final int DEMAND_POOL_LIMIT = 500;
    static final int MAX_PER_RUN = 100;

    int indexStaleCartProducts(LocalDateTime since) {
        List<Long> pool = cartDemandReader.findRecentProductIdsByDemand(since, DEMAND_POOL_LIMIT);
        if (pool.isEmpty()) {
            return 0;
        }
        Set<Long> upToDate = productVectorIndex.findUpToDate(pool, productEmbeddingClient.primaryModel(),
                embeddingTextBuilder.version());
        List<Long> stale = pool.stream()
                .filter(productId -> !upToDate.contains(productId))
                .toList();
        List<ProductToIndex> products = new ArrayList<>();
        for (int from = 0; from < stale.size() && products.size() < MAX_PER_RUN; from += MAX_PER_RUN) {
            List<Long> chunk = stale.subList(from, Math.min(stale.size(), from + MAX_PER_RUN));
            Map<Long, CartProductProfile> profiles = productClient.findAllProductProfiles(chunk).stream()
                    .filter(ProductVectorIndexingUseCase::isIndexable)
                    .collect(Collectors.toMap(CartProductProfile::productId, Function.identity(), (first, second) -> first));
            chunk.stream()
                    .map(profiles::get)
                    .filter(Objects::nonNull)
                    .limit(MAX_PER_RUN - products.size())
                    .map(profile -> new ProductToIndex(profile.productId(), profile.categoryId(), profile.productName()))
                    .forEach(products::add);
            if (profiles.size() < chunk.size()) {
                log.info("Skipped {} cart products without a name or category for vector indexing",
                        chunk.size() - profiles.size());
            }
        }
        return index(products);
    }

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

    private static boolean isIndexable(CartProductProfile profile) {
        return profile.categoryId() != null && profile.productName() != null && !profile.productName().isBlank();
    }
}
