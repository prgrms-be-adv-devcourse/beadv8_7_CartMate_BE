package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.code.ErrorCode;
import com.cartmate.cartmate.global.exception.DomainException;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSaveUseCase {
    private final ProductRepository productRepository;


    public void publishProduct(List<CreateProduct> productList) {
        productList.forEach(createProduct -> {
            Product product = productRepository.findById(createProduct.getId())
                    .orElseThrow(() -> new DomainException(ErrorCode.PRODUCT_UPDATE_FAILED));
            product.publish(createProduct);
        });
    }
}
