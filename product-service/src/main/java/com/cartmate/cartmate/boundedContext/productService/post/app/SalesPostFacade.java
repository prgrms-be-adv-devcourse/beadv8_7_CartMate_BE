package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.post.domain.*;
import com.cartmate.cartmate.boundedContext.productService.post.dto.*;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import com.cartmate.cartmate.boundedContext.productService.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.RollbackProductRequest;
import com.cartmate.cartmate.boundedContext.productService.event.PostApiCallFailedEvent;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProductRequest;
import com.cartmate.cartmate.boundedContext.productService.config.ProductServiceConsumers;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import com.cartmate.cartmate.shared.product.event.ProductsDeletedByPostEvent;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.admin.AdminSalesPostCountResponse;
import com.cartmate.cartmate.boundedContext.productService.post.dto.admin.AdminSalesPostDetailResponse;
import com.cartmate.cartmate.boundedContext.productService.post.dto.admin.AdminSalesPostSummaryResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostFacade {
    private final ProcessedEvents processedEvents;
    private final SalesPostSyncSellerUseCase salesPostSyncSellerUseCase;
    private final SalesPostSyncProductUseCase salesPostSyncProductUseCase;
    private final SalesPostCreateUseCase salesPostCreateUseCase;
    private final SalesPostSelectUseCase salesPostSelectUseCase;
    private final SalesPostRecoveryUseCase salesPostRecoveryUseCase;
    private final SalesPostUpdateUseCase salesPostUpdateUseCase;
    private final SalesPostRecoverUseCase salesPostRecoverUseCase;
    private final SalesPostDeleteUseCase salesPostDeleteUseCase;
    private final TransactionTemplate transactionTemplate;
    private final SalesPostApiClient salesPostApiClient;
    private final SalesPostSupport salesPostSupport;
    private final AdminSalesPostUseCase adminSalesPostUseCase;
    private final SalesPostHideUseCase salesPostHideUseCase;
    private final SalesPostSyncSellerTrustUseCase salesPostSyncSellerTrustUseCase;

    @Transactional
    public SalesPostSeller syncSeller(SellerRegisteredEvent seller) {
        return salesPostSyncSellerUseCase.syncSeller(seller);
    }

    // 신뢰도 변경 이벤트를 받아 판매자의 신뢰도 복제본을 동기화한다.
    @Transactional
    public void syncSellerTrust(SellerTrustScoreChangedEvent event) {
        salesPostSyncSellerTrustUseCase.syncSellerTrust(event);
    }

    public void registerSalesPost(CreateSalesPostRequest request, List<MultipartFile> images) {
        SalesPost post = transactionTemplate.execute(status -> {
            return salesPostCreateUseCase.saveSalesPost(request);
        });

        try{
            CreateProductRequest productRequest = new CreateProductRequest(post.getId(), request.toCreateProductList());
            salesPostApiClient.registerProducts(productRequest, images);

            transactionTemplate.executeWithoutResult(status ->
                    salesPostCreateUseCase.completeSave(post.getId())
            );
        } catch(Exception e) {
            salesPostDeleteUseCase.deleteSalesPostByEvent(post.getId());
            GlobalConfig.getEventPublisher().publish(
                    new PostApiCallFailedEvent(
                            new RollbackProductRequest(post.getId())
                    )
            );
            throw e;
        }
    }

    // 판매글을 DELETING으로 바꾸고 SalesPostDeletedEvent를 outbox에 남긴다.
    // 상품 삭제 → 복제본 삭제 → 삭제 완료는 이벤트로 이어지고, 실패하면 outbox · inbox가 재시도한다
    @Transactional
    public void deleteSalesPostByUser(Long postId, Long sellerId) {
        salesPostDeleteUseCase.deleteSalesPostByUser(postId, sellerId);
    }

    // 삭제 대사: 오래 DELETING에 멈춘 판매글
    public List<Long> findStuckDeletingPostIds(int limit) {
        return salesPostDeleteUseCase.findStuckDeletingIds(limit);
    }

    // 삭제 대사: 삭제 이벤트 재발행. 그 사이 삭제가 끝났으면 false
    public boolean republishStuckDelete(Long postId) {
        return salesPostDeleteUseCase.republishDelete(postId);
    }

    @Transactional
    public void syncProduct(SyncProductRequest request) {
        salesPostSyncProductUseCase.syncProduct(request.getProductList());
    }

    @Transactional(readOnly = true)
    public RsData<PageResponse<SelectSalesPostResponse>> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable) {
        return new RsData<>(
                "200-1",
                "판매목록 조회에 성공했습니다.",
                salesPostSelectUseCase.selectSalesPostAll(request, pageable)
        );
    }

    @Transactional(readOnly = true)
    public RsData<SelectSalesPostResponse> selectSalesPostDetail(Long postId) {
        return new RsData<>(
                "200-2",
                "판매글 상세조회에 성공했습니다.",
                salesPostSelectUseCase.selectSalesPostDetail(postId)
        );
    }

    // product가 상품 삭제를 마치면(ProductsDeletedByPostEvent) 복제본을 지우고 판매글 삭제를 완료한다.
    // 같은 이벤트는 한 번만 처리하고, 판매글이 DELETING이 아니면(이미 완료 등) 아무것도 하지 않는다
    @Transactional
    public void completeDeleteByEvent(String eventId, ProductsDeletedByPostEvent event) {
        if (!processedEvents.markFirst(ProductServiceConsumers.PRODUCTS_DELETED_BY_POST, eventId)) {
            return;
        }

        salesPostSyncProductUseCase.deleteProductByPost(event.productIds());
        salesPostDeleteUseCase.completeDelete(event.postId())
                // 삭제도 진행 중 거래 입장에서는 숨김과 같다
                .ifPresent(post -> salesPostHideUseCase.publishHidden(
                        post, event.productIds(), SalesPostHideType.SELLER_DELETED, null, null));
    }

    @Transactional
    public void deleteProductByUser(DeleteProductRequest request) {
        salesPostSyncProductUseCase.deleteProductByUser(request);
    }

    public void updateSalesPostByUser(UpdateSalesPostRequest request, List<MultipartFile> images) {
        Long postId = request.getId();
        SalesPostUpdateStatus previousUpdatedStatus = transactionTemplate.execute(s -> {
            SalesPost post = salesPostSupport.validateUpdatable(request);
            SalesPostUpdateStatus beforeUpdateStatus = salesPostUpdateUseCase.markPending(post);
            salesPostRecoveryUseCase.replace(postId, beforeUpdateStatus); // 복구 데이터 생성(스냅샷 제외)
            return beforeUpdateStatus;
        });

        try {
            List<SelectProductResponse> products = salesPostApiClient.getProduct(postId);
            SalesPostSnapshot snapshot = SalesPostSnapshot.from(products);
            transactionTemplate.executeWithoutResult(s ->
                    salesPostRecoveryUseCase.saveSnapshot(postId, snapshot) // 스냅샷 저장
            );
        } catch (RuntimeException e) {
            // 상품 수정 전이라 상품은 그대로이므로 상태만 되돌림
            transactionTemplate.executeWithoutResult(s -> {
                salesPostRecoveryUseCase.delete(postId);
                salesPostUpdateUseCase.revertTo(postId, previousUpdatedStatus);
            });
            throw e;
        }

        try {
            salesPostApiClient.updateProducts(request, images);
        } catch (ServiceException e) {
            if (e.getResultCode().equals(ProductErrorCode.PRODUCT_UPDATE_INVALID.resultCode())) {
                // 명확한 실패(상품 쪽 rollback 완료)
                transactionTemplate.executeWithoutResult(s -> {
                    salesPostRecoveryUseCase.delete(postId);
                    salesPostUpdateUseCase.revertTo(postId, previousUpdatedStatus);
                });
            } else {
                salesPostRecoverUseCase.recover(postId);
            }
            throw e;
        } catch (RuntimeException e) {
            salesPostRecoverUseCase.recover(postId);
            throw e;
        }

        try {
            transactionTemplate.executeWithoutResult(s -> {
                salesPostUpdateUseCase.markCompleted(postId);
                salesPostUpdateUseCase.updateSalesPostByUser(request);
                salesPostRecoveryUseCase.delete(postId);
            });
        } catch (RuntimeException e) {
            salesPostRecoverUseCase.recover(postId);
            throw e;
        }
    }

    // ===== 관리자 =====
    @Transactional(readOnly = true)
    public PageResponse<AdminSalesPostSummaryResponse> getAdminSalesPosts(SalesPostSaleStatus status, String keyword, Pageable pageable) {
        return adminSalesPostUseCase.getList(status, keyword, pageable);
    }

    @Transactional(readOnly = true)
    public AdminSalesPostCountResponse getAdminSalesPostCounts() {
        return adminSalesPostUseCase.getCounts();
    }

    @Transactional(readOnly = true)
    public AdminSalesPostDetailResponse getAdminSalesPostDetail(Long postId) {
        return adminSalesPostUseCase.getDetail(postId);
    }

    public void suspendSalesPostByAdmin(Long postId, Long adminId, String reason) {
        adminSalesPostUseCase.suspend(postId, adminId, reason);
    }

    public void resumeSalesPostByAdmin(Long postId, Long adminId, String reason) {
        adminSalesPostUseCase.resume(postId, adminId, reason);
    }

    // ===== 판매자 내림·재개 =====
    public void pauseSalesPostBySeller(Long postId, Long sellerId, Long memberId) {
        salesPostHideUseCase.pauseBySeller(postId, sellerId, memberId);
    }

    public void resumeSalesPostBySeller(Long postId, Long sellerId) {
        salesPostHideUseCase.resumeBySeller(postId, sellerId);
    }

    // 회원 탈퇴 신청: 판매자의 판매 중인 판매글 ID
    @Transactional(readOnly = true)
    public List<Long> findOnSalePostIdsOfSeller(Long sellerId) {
        return salesPostHideUseCase.findOnSalePostIdsOfSeller(sellerId);
    }

}
