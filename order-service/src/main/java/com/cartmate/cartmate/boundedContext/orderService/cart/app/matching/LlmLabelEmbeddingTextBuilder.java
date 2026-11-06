package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.EmbeddingText;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductToIndex;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductLabelClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "custom.matching.embedding-text", havingValue = "llm")
class LlmLabelEmbeddingTextBuilder implements EmbeddingTextBuilder {

    static final String VERSION = "llm-v1";

    private final ProductLabelClient productLabelClient;

    @Override
    public List<EmbeddingText> build(List<ProductToIndex> products) {
        List<String> labels = productLabelClient.labelProductTypes(products.stream().map(ProductToIndex::name).toList());
        boolean allLabeled = labels.size() == products.size()
                && labels.stream().allMatch(label -> label != null && !label.isBlank());
        if (!allLabeled) {
            return products.stream()
                    .map(product -> new EmbeddingText(RuleBasedEmbeddingTextBuilder.normalize(product.name()),
                            RuleBasedEmbeddingTextBuilder.VERSION))
                    .toList();
        }
        return labels.stream()
                .map(label -> new EmbeddingText(label.strip(), VERSION))
                .toList();
    }
}
