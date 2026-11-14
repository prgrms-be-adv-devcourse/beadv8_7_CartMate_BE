package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.dto.CategoryResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CompleteProductUpdateRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductOptionTypeResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SelectProductResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductUpdateResult;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductsRequest;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.product.dto.*;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.productService.config.ProductServiceConsumers;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
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
    private final ProductQueryUseCase productQueryUseCase;
    private final ProductSupport productSupport;
    private final TransactionTemplate transactionTemplate;
    private final ProductStatusChangeUseCase productStatusChangeUseCase;
    private final ProductImageOrphanCleanUseCase productImageOrphanCleanUseCase;
    private final ProductOptionQueryUseCase productOptionQueryUseCase;
    private final CategoryQueryUseCase categoryQueryUseCase;
    private final InventoryReservationUseCase inventoryReservationUseCase;

    // 옵션 종류 전체 + 종류별 상품옵션 (상품 등록 화면의 옵션 선택 목록)
    public List<ProductOptionTypeResponse> findProductOptions() {
        return productOptionQueryUseCase.findAll();
    }

    // 옵션 종류 하나의 상품옵션
    public ProductOptionTypeResponse findProductOptions(Long typeId) {
        return productOptionQueryUseCase.findByType(typeId);
    }

    // 카테고리 전체 (상품 등록 화면의 카테고리 선택 목록)
    public List<CategoryResponse> findCategories() {
        return categoryQueryUseCase.findAll();
    }

    // 저장한 상품(사진 · 옵션 포함)을 돌려준다. post가 이 응답으로 자기 마무리 트랜잭션에서 복제본을 만든다
    public List<SyncProduct> registerProduct(CreateProductRequest request, List<MultipartFile> images) {
        productSupport.validateProductCount(request.getProductList());
        productSupport.includeFileData(request, images);
        productSupport.validateProductFormat(request.getProductList());

        List<UploadedImage> uploadedImageList = productImageFileUploadUseCase.uploadAll(request);
        try {
            return transactionTemplate.execute(status ->
                    productCreateUseCase.saveProduct(request, uploadedImageList));
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

    // 판매글 수정 끝: 수정 중(UPDATING)인 상품을 수정 전 상태로 되돌려 다시 구매할 수 있게 한다
    public void completeProductUpdate(Long postId, CompleteProductUpdateRequest request) {
        productUpdateUseCase.completeUpdate(postId, request);
    }

    // 수정한 상품(사진 · 옵션 포함)을 돌려준다. post가 이 응답으로 수정 마무리 트랜잭션에서 복제본을 갱신한다
    public List<SyncProduct> updateProducts(UpdateProductsRequest request, List<MultipartFile> images) {
        productSupport.validateUpdateProductCount(request.getProductList());
        productSupport.includeFileDataUpdate(request, images);
        productSupport.validateUpdateProductFormat(request.getProductList());

        List<UploadedImage> uploadedImageList = productImageFileUpdateUseCase.uploadAll(request);

        ProductUpdateResult result;
        try {
            result = transactionTemplate.execute(status ->
                    productUpdateUseCase.updateProduct(request, uploadedImageList));
        } catch (RuntimeException e) {
            productImageFileDeleteUseCase.deleteImageByException(uploadedImageList);
            throw e;
        }

        productImageFileDeleteUseCase.deleteByKeys(result.deletedKeys());
        return result.syncProductList();
    }

    // 판매글 수정 실패 시 스냅샷으로 복구 (이미지 파일은 복구하지 않음).
    // 되돌린 상품을 돌려준다. post가 이 응답으로 복구 성공 트랜잭션에서 복제본도 되돌린다
    public List<SyncProduct> restoreProducts(Long postId, RestoreProductsRequest request) {
        if (request.getProductList() == null || request.getProductList().isEmpty()) {
            return List.of();
        }

        ProductUpdateResult result = transactionTemplate.execute(status ->
                productRestoreUseCase.restoreProduct(postId, request));

        try {
            productImageFileDeleteUseCase.deleteByKeys(result.deletedKeys());
        } catch (RuntimeException e) {
            log.warn("복구 후 이미지 파일 삭제 실패. postId={}", postId, e);
        }
        return result.syncProductList();
    }

    @Transactional(readOnly = true)
    public List<ProductForPurchase> findProductsForPurchase(Collection<Long> productIds) {
        return productQueryUseCase.findProductsForPurchase(productIds);
    }

    public List<InventoryForPurchase> findInventoriesForPurchase(Collection<Long> inventoryIds) {
        return productQueryUseCase.findInventoriesForPurchase(inventoryIds);
    }

    public List<InventoryForPurchase> findInventoriesForPurchaseByProducts(Collection<Long> productIds) {
        return productQueryUseCase.findInventoriesForPurchaseByProducts(productIds);
    }

    // 트랜잭션 밖에서 잡는다: 같은 주문의 다른 요청이 먼저 선점해 이 요청이 유니크 키 · 버전 충돌로 롤백된 경우
    // 결과는 "이미 선점됨"과 같으니 성공으로 끝낸다(순서대로 온 재시도와 같은 응답)
    public void reserveInventories(InventoryReserveRequest request) {
        try {
            inventoryReservationUseCase.reserve(request);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            log.info("같은 주문 동시 선점 — 먼저 온 요청이 선점함: order={} #{}", request.orderType(), request.orderId());
        }
    }

    public void releaseInventories(InventoryReleaseRequest request) {
        inventoryReservationUseCase.release(request.orderType(), request.orderId());
    }

    public void confirmInventories(OrderType orderType, Long orderId) {
        inventoryReservationUseCase.confirm(orderType, orderId);
    }

    public void cancelInventories(OrderType orderType, Long orderId) {
        inventoryReservationUseCase.cancel(orderType, orderId);
    }

    public void cancelSellerInventories(Long orderId, Long sellerId) {
        inventoryReservationUseCase.cancelSeller(orderId, sellerId);
    }

    @Transactional(readOnly = true)
    public RsData<List<SelectProductResponse>> selectProductsByPost(Long postId) {
        return new RsData<>(
                "200-1",
                "상품 조회에 성공했습니다.",
                productQueryUseCase.selectProductsByPost(postId)
        );
    }

    // 판매글 등록 실패(SalesPostRegisterFailedEvent)에 의한 보상. 같은 이벤트는 한 번만 처리한다
    // 처리 기록 + 상품 삭제를 한 트랜잭션으로 커밋한 뒤에 사진 파일을 지운다(먼저 지우면 DB 롤백 시 파일 없는 상품이 남는다).
    // 파일 삭제 실패는 고아 파일로만 남으므로 로그만 남긴다
    public void rollbackByApiFail(String eventId, Long postId) {
        List<String> imageKeys = transactionTemplate.execute(status -> {
            if (!processedEvents.markFirst(ProductServiceConsumers.SALES_POST_REGISTER_FAILED, eventId)) {
                return List.<String>of();
            }
            return productDeleteUseCase.hardDeleteByEvent(postId);
        });

        try {
            productImageFileDeleteUseCase.deleteByKeys(imageKeys);
        } catch (RuntimeException e) {
            log.warn("등록 보상 후 이미지 파일 삭제 실패. postId={}", postId, e);
        }
    }

    public void changeStatusByPost(Long postId, ProductStatus status) {
        productStatusChangeUseCase.changeStatusByPost(postId, status);
    }

    // 고아 사진 파일 정리. 고아 파일 수를 돌려준다(dryRun이면 지우지 않고 세기만)
    public int cleanUpOrphanImages(Instant uploadedBefore, boolean dryRun) {
        return productImageOrphanCleanUseCase.cleanUp(uploadedBefore, dryRun);
    }
}
