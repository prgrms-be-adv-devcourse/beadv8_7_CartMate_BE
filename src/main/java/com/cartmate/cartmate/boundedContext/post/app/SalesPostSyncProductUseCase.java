package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProductOption;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.shared.post.dto.ProductSyncUpdateRequest;
import com.cartmate.cartmate.shared.post.dto.productSyncCompletedEvent;
import com.cartmate.cartmate.shared.product.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostSyncProductUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostProductRepository salesPostProductRepository;
    private final SalesPostProductImageRepository salesPostProductImageRepository;
    private final SalesPostProductOptionRepository salesPostProductOptionRepository;

    @Transactional
    public void syncProduct(List<SyncProduct> syncList) {
        if (syncList == null || syncList.isEmpty()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_SYNC_DATA_NOT_FOUND);
        }

        SalesPost salesPost = salesPostRepository.findById(syncList.getFirst().getSalesPostId())
                    .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        List<Long> successList = new ArrayList<>();
        syncList.forEach(product -> {
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

            List<SyncProductImage> imageList = product.getProductImageList();
            if (imageList != null) {
                imageList.forEach(image -> {
                    SalesPostProductImage newImage = image.getNewImage(newProduct);
                    salesPostProductImageRepository.save(newImage);
                });
            }

            List<SyncProductOption> optionList = product.getProductOptionList();
            if (optionList != null) {
                optionList.forEach(option -> {
                    SalesPostProductOption newOption = option.getNewOption(newProduct);
                    salesPostProductOptionRepository.save(newOption);
                });
            }
            successList.add(product.getId());
        });

        GlobalConfig.getEventPublisher().publish(
                new productSyncCompletedEvent(
                        new ProductSyncUpdateRequest(successList)
                )
        );
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
