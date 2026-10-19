package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.shared.product.dto.DeleteProductListRequest;
import com.cartmate.cartmate.shared.product.dto.SyncProduct;
import com.cartmate.cartmate.shared.product.event.ProductListDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductDeleteUseCase {
    private final ProductRepository productRepository;

    @Transactional
    public void deleteProductByUser(Long productId, Long sellerId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStatus() != ProductStatus.DELETED)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSellerId().equals(sellerId)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        product.delete();

//        GlobalConfig.getEventPublisher().publish(
//                new ProductDeletedEvent(
//                        new DeletePostRequest(product.getSellerId(), product.getId())
//                )
//        );
    }

    @Transactional
    public void deleteProductByPost(Long postId) {
        List<SyncProduct> deletedList = productRepository.findBySalesPostId(postId).stream()
                .map(Product::delete)
                .map(Product::toSyncProduct)
                .toList();

        GlobalConfig.getEventPublisher().publish(
                new ProductListDeletedEvent(new DeleteProductListRequest(deletedList))
        );
    }
}
