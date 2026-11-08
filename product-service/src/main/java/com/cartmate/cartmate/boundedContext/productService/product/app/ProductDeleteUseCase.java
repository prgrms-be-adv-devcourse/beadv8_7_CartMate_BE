package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.shared.product.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.boundedContext.productService.product.dto.DeleteProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.DeleteProductListRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.event.ProductDeletedEvent;
import com.cartmate.cartmate.boundedContext.productService.event.ProductListDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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

        GlobalConfig.getEventPublisher().publish(
                new ProductDeletedEvent(
                        new DeleteProductRequest(product.getId())
                )
        );
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

    // 판매글 등록 보상: 상품을 DB에서 지우고, 지운 상품의 사진 키를 돌려준다 (행이 사라지면 키도 알 수 없으므로 지우기 전에 모은다)
    @Transactional
    public List<String> hardDeleteByEvent(Long postId) {
        List<Product> products = productRepository.findBySalesPostId(postId);

        List<String> imageKeys = products.stream()
                .flatMap(product -> product.getProductImageList().stream())
                .map(ProductImage::getS3Key)
                .filter(Objects::nonNull)
                .toList();

        productRepository.deleteAll(products);
        return imageKeys;
    }
}
