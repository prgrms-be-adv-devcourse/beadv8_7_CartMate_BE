package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductDeleteUseCase {
    private final ProductRepository productRepository;

    @Transactional
    public void deleteProductByUser(Long postId) {
        List<Product> productList = productRepository.findBySalesPostId(postId);
        if (productList.isEmpty()) {
            return;
        }
        productList.forEach(Product::delete);
    }

    @Transactional
    public void deleteProductByEvent(Long postId) {
        List<Product> productList = productRepository.findBySalesPostId(postId);
        if (productList.isEmpty()) {
            return;
        }
        productList.forEach(Product::delete);
    }
}
