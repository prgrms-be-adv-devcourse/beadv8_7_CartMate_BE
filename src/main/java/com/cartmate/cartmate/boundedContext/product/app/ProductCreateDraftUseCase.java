package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCreateDraftUseCase {
    private final ProductRepository productRepository;

    public List<CreateProduct> createProductDraft(CreateProductRequest request) {
        request.getProductList().forEach(createProduct -> {
            Product product = productRepository.save(new Product(
                    createProduct.getSellerId(),
                    createProduct.getSalesPostId(),
                    0L,
                    "Unknown",
                    "No description",
                    0L
            ));
            createProduct.assignProductId(product.getId());
        });

        return request.getProductList();
    }
}
