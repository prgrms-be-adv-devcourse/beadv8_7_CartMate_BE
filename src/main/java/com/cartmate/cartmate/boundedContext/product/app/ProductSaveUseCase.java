package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSaveUseCase {
    private final ProductRepository productRepository;

    @Transactional
    public void publishProduct(List<CreateProduct> productList) {
        productList.forEach(createProduct -> {
            Product product = productRepository.findById(createProduct.getId())
                    .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_UPDATE_FAILED));
            product.publish(createProduct);
        });
    }
}
