package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductUpdateSyncStatusUseCase {
    private final ProductRepository productRepository;

    public void updateSyncStatus(List<Long> successList) {
        productRepository.findAllById(successList)
                .forEach(Product::updateSyncStatus);
    }
}
