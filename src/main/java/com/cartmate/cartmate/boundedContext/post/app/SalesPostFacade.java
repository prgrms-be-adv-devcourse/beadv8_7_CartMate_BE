package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.*;
import com.cartmate.cartmate.boundedContext.post.dto.*;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.shared.post.dto.RollbackProductRequest;
import com.cartmate.cartmate.shared.post.event.PostApiCallFailedEvent;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.DeleteProductListRequest;
import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostCountResponse;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostDetailResponse;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostSummaryResponse;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import org.springframework.data.domain.Pageable;
import java.util.List;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostFacade {
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
    private final SalesPostProductRepository salesPostProductRepository;

    @Transactional
    public SalesPostSeller syncSeller(SellerRegisteredEvent seller) {
        return salesPostSyncSellerUseCase.syncSeller(seller);
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

    public void deleteSalesPostByUser(Long postId, Long sellerId) {
        SalesPost post = transactionTemplate.execute(status ->
                salesPostDeleteUseCase.deleteSalesPostByUser(postId, sellerId));

        // 상품 삭제 API가 판매글 쪽 상품 복제본까지 지울 수 있어서, 호출 전에 상품 ID를 모아 둔다
        List<Long> productIds = salesPostProductRepository.findIdsBySalesPostId(postId);

        try {
            salesPostApiClient.deleteProducts(post.getId());
            transactionTemplate.executeWithoutResult(status -> {
                SalesPost deleted = salesPostDeleteUseCase.completeDelete(post.getId());
                // 삭제도 진행 중 거래 입장에서는 숨김과 같다
                salesPostHideUseCase.publishHidden(deleted, productIds, SalesPostHideType.SELLER_DELETED, null, null);
            });
        } catch (ServiceException e) {
            throw e;
        }
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

    @Transactional
    public void deleteProductByPost(DeleteProductListRequest request) {
        salesPostSyncProductUseCase.deleteProductByPost(request);
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
            if (e.getResultCode().equals("PRODUCT_UPDATE_INVALID")) {
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

}
