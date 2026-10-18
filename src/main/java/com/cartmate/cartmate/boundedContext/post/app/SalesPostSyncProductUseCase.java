package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.shared.product.dto.SyncProduct;
import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostSyncProductUseCase {
    private final SalesPostProductRepository salesPostProductRepository;
    private final SalesPostProductImageRepository salesPostProductImageRepository;

    public void syncProduct(SyncProductRequest request) {
        List<SyncProduct> syncProductList = request.getProductList();
        syncProductList.forEach(product -> {
            SalesPostProduct newProduct = new SalesPostProduct(
                    product.getId(),
                    product.getSellerId(),
                    product.getSalesPostId(),
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
                        image.getCreateDate(),
                        image.getModifyDate()
                );
                salesPostProductImageRepository.save(newImage);
            });
        });
    }
}
