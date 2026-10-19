package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.shared.product.dto.DeleteProductListRequest;
import com.cartmate.cartmate.shared.product.dto.SyncProduct;
import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostSyncProductUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostProductRepository salesPostProductRepository;
    private final SalesPostProductImageRepository salesPostProductImageRepository;

    @Transactional
    public void syncProduct(SyncProductRequest request) {
        if (request.getProductList() == null || request.getProductList().isEmpty()) {
            throw new ServiceException(SalesPostErrorCode.POST_NOT_FOUND);
        }

        SalesPost salesPost = salesPostRepository.findById(request.getProductList().getFirst().getSalesPostId())
                    .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        List<SyncProduct> syncProductList = request.getProductList();
        syncProductList.forEach(product -> {
            SalesPostProduct newProduct = new SalesPostProduct(
                    product.getId(),
                    product.getSellerId(),
                    salesPost,
                    product.getCategoryId(),
                    product.getName(),
                    product.getDescription(),
                    product.getBasePrice(),
                    ProductStatus.ON_SALE,
                    product.getCreateDate(),
                    product.getModifyDate()
            );
            salesPostProductRepository.save(newProduct);

            product.getProductImageList().forEach(image -> {
                SalesPostProductImage newImage = new SalesPostProductImage(
                        image.getId(),
                        image.getSellerId(),
                        newProduct,
                        image.getS3Key(),
                        image.getImageSeq(),
                        image.getStatus(),
                        image.getCreateDate(),
                        image.getModifyDate()
                );
                salesPostProductImageRepository.save(newImage);
            });
        });
    }

    @Transactional
    public void deleteProductByPost(DeleteProductListRequest request) {
        List<Long> productIds = request.getProductList().stream()
                .map(SyncProduct::getId)
                .toList();

        salesPostProductRepository.findByIdIn(productIds)
                .forEach(SalesPostProduct::delete);
    }

    @Transactional
    public void deleteProductByUser(DeleteProductRequest request) {
        salesPostProductRepository.findById(request.getProductId())
                .ifPresent(SalesPostProduct::delete);
    }
}
