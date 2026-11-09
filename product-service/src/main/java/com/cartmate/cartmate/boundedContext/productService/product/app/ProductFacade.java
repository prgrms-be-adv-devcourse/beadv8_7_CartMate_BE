package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.dto.SelectProductResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductUpdateResult;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductsRequest;
import com.cartmate.cartmate.shared.product.dto.*;
import com.cartmate.cartmate.shared.product.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UploadedImage;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.boundedContext.productService.config.ProductServiceConsumers;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductSyncUpdateRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RollbackProductRequest;
import com.cartmate.cartmate.boundedContext.productService.event.ProductCreatedEvent;
import com.cartmate.cartmate.boundedContext.productService.event.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductFacade {
    private final ProcessedEvents processedEvents;
    private final ProductCreateUseCase productCreateUseCase;
    private final ProductUpdateUseCase productUpdateUseCase;
    private final ProductRestoreUseCase productRestoreUseCase;
    private final ProductImageFileUploadUseCase productImageFileUploadUseCase;
    private final ProductImageFileUpdateUseCase productImageFileUpdateUseCase;
    private final ProductImageFileDeleteUseCase productImageFileDeleteUseCase;
    private final ProductDeleteUseCase productDeleteUseCase;
    private final ProductUpdateSyncStatusUseCase productUpdateSyncStatusUseCase;
    private final ProductQueryUseCase productQueryUseCase;
    private final ProductSupport productSupport;
    private final TransactionTemplate transactionTemplate;
    private final ProductStatusChangeUseCase productStatusChangeUseCase;

    public void registerProduct(CreateProductRequest request, List<MultipartFile> images) {
        productSupport.validateProductCount(request.getProductList());
        productSupport.includeFileData(request, images);
        productSupport.validateProductFormat(request.getProductList());

        List<UploadedImage> uploadedImageList = productImageFileUploadUseCase.uploadAll(request);
        try {
            List<SyncProduct> syncProductList = transactionTemplate.execute(status -> {
                List<SyncProduct> productList = productCreateUseCase.saveProduct(request, uploadedImageList);

                GlobalConfig.getEventPublisher().publish(
                        new ProductCreatedEvent(
                                new SyncProductRequest(productList)
                        )
                );

                return productList;
            });
        } catch (RuntimeException e) {
            productImageFileDeleteUseCase.deleteImageByException(uploadedImageList);
            throw e;
        }
    }

    // 사용자 직접 삭제
    @Transactional
    public void deleteProductByUser(Long productId, Long sellerId) {
        productDeleteUseCase.deleteProductByUser(productId, sellerId);
    }

    // 판매글 삭제(SalesPostDeletedEvent)에 의한 삭제. 같은 이벤트는 한 번만 처리한다
    @Transactional
    public void deleteProductByPost(String eventId, Long postId) {
        if (!processedEvents.markFirst(ProductServiceConsumers.SALES_POST_DELETED, eventId)) {
            return;
        }
        productDeleteUseCase.deleteProductByPost(postId);
    }

    @Transactional
    public void updateSyncStatus(ProductSyncUpdateRequest request) {
        productUpdateSyncStatusUseCase.updateSyncStatus(request.getSuccessList());
    }

    public void updateProducts(UpdateProductsRequest request, List<MultipartFile> images) {
        productSupport.validateUpdateProductCount(request.getProductList());
        productSupport.includeFileDataUpdate(request, images);
        productSupport.validateUpdateProductFormat(request.getProductList());

        List<UploadedImage> uploadedImageList = productImageFileUpdateUseCase.uploadAll(request);

        ProductUpdateResult result;
        try {
            result = transactionTemplate.execute(status -> {
                ProductUpdateResult updateResult = productUpdateUseCase.updateProduct(request, uploadedImageList);

                GlobalConfig.getEventPublisher().publish(
                        new ProductUpdatedEvent(
                                new SyncProductRequest(updateResult.syncProductList())
                        )
                );
                return updateResult;
            });
        } catch (RuntimeException e) {
            productImageFileDeleteUseCase.deleteImageByException(uploadedImageList);
            throw e;
        }

        productImageFileDeleteUseCase.deleteByKeys(result.deletedKeys());
    }

    // 판매글 수정 실패 시 스냅샷으로 복구 (이미지 파일은 복구하지 않음)
    public void restoreProducts(Long postId, RestoreProductsRequest request) {
        if (request.getProductList() == null || request.getProductList().isEmpty()) {
            return;
        }

        ProductUpdateResult result = transactionTemplate.execute(status -> {
            ProductUpdateResult restoreResult = productRestoreUseCase.restoreProduct(postId, request);

            GlobalConfig.getEventPublisher().publish(
                    new ProductUpdatedEvent(
                            new SyncProductRequest(restoreResult.syncProductList())
                    )
            );
            return restoreResult;
        });

        try {
            productImageFileDeleteUseCase.deleteByKeys(result.deletedKeys());
        } catch (RuntimeException e) {
            log.warn("복구 후 이미지 파일 삭제 실패. postId={}", postId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductForPurchase> findProductsForPurchase(Collection<Long> productIds) {
        return productQueryUseCase.findProductsForPurchase(productIds);
    }

    @Transactional(readOnly = true)
    public RsData<List<SelectProductResponse>> selectProductsByPost(Long postId) {
        return new RsData<>(
                "200-1",
                "상품 조회에 성공했습니다.",
                productQueryUseCase.selectProductsByPost(postId)
        );
    }

    // api에 의한 보상트랜잭션 처리.
    // 상품 삭제가 커밋된 뒤에 사진 파일을 지운다(먼저 지우면 DB 롤백 시 파일 없는 상품이 남는다). 파일 삭제 실패는 고아 파일로만 남으므로 로그만 남긴다
    public void rollbackByApiFail(RollbackProductRequest rollbackProductRequest) {
        Long postId = rollbackProductRequest.getPostId();
        List<String> imageKeys = productDeleteUseCase.hardDeleteByEvent(postId);

        try {
            productImageFileDeleteUseCase.deleteByKeys(imageKeys);
        } catch (RuntimeException e) {
            log.warn("등록 보상 후 이미지 파일 삭제 실패. postId={}", postId, e);
        }
    }

    public void changeStatusByPost(Long postId, ProductStatus status) {
        productStatusChangeUseCase.changeStatusByPost(postId, status);
    }
}
