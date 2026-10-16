package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCreateUseCase {
    private final ProductRepository productRepository;

    @Transactional
    public List<CreateProduct> saveProduct(CreateProductRequest request) {
        request.getProductList().forEach(createProduct -> {
            Product product = productRepository.save(new Product(
                    createProduct.getSellerId(),
                    createProduct.getSalesPostId(),
                    createProduct.getCategoryId(),
                    createProduct.getName(),
                    createProduct.getDescription(),
                    createProduct.getBasePrice()
            ));
            createProduct.assignProductId(product.getId());
        });

        return request.getProductList();
    }
}
