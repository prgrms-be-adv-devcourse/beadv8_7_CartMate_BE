package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.post.dto.SelectProductResponse;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.shared.post.dto.ProductSyncUpdateRequest;
import com.cartmate.cartmate.shared.post.dto.RollbackProductRequest;
import com.cartmate.cartmate.shared.product.dto.*;
import com.cartmate.cartmate.shared.product.event.ProductCreatedEvent;
import com.cartmate.cartmate.shared.product.event.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductFacade {
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

    // Post에 의한 삭제
    @Transactional
    public void deleteProductByPost(Long postId) {
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

    // api에 의한 보상트랜잭션 처리
    @Transactional
    public void rollbackByApiFail(RollbackProductRequest rollbackProductRequest) {
        productDeleteUseCase.hardDeleteByEvent(rollbackProductRequest.getPostId());
    }

    public void changeStatusByPost(Long postId, ProductStatus status) {
        productStatusChangeUseCase.changeStatusByPost(postId, status);
    }
}
