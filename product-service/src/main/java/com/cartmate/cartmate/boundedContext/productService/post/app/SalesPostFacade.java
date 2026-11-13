package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.post.domain.*;
import com.cartmate.cartmate.boundedContext.productService.post.dto.*;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.post.event.SalesPostRegisterFailedEvent;
import com.cartmate.cartmate.boundedContext.productService.config.ProductServiceConsumers;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import com.cartmate.cartmate.shared.product.event.InventoryRemovedEvent;
import com.cartmate.cartmate.shared.product.event.ProductDeletedEvent;
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
    private final DomainEventPublisher domainEventPublisher;
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
            List<SyncProduct> savedProducts = salesPostApiClient.registerProducts(productRequest, images);

            // 복제본 생성과 판매 시작을 한 트랜잭션으로. 복제본을 못 만들면 등록 실패로 보고 아래에서 보상한다
            transactionTemplate.executeWithoutResult(status -> {
                salesPostSyncProductUseCase.syncProduct(savedProducts);
                salesPostCreateUseCase.completeSave(post.getId());
            });
        } catch(Exception e) {
            // 판매글 삭제와 상품 정리 이벤트(outbox)를 한 트랜잭션으로. 커밋되면 outbox가 product까지 전달을 책임진다
            transactionTemplate.executeWithoutResult(status -> {
                salesPostDeleteUseCase.deleteSalesPostByEvent(post.getId());
                domainEventPublisher.publish(new SalesPostRegisterFailedEvent(post.getId()));
            });
            throw e;
        }
    }

    // 판매글을 DELETING으로 바꾸고 SalesPostDeletedEvent를 outbox에 남긴다.
    // 상품 삭제 → 복제본 삭제 → 삭제 완료는 이벤트로 이어지고, 실패하면 outbox(Kafka를 켜면 컨슈머)가 재시도한다
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

    // 판매자가 상품 하나를 삭제하면(ProductDeletedEvent) 그 복제본을 지운다. 같은 이벤트는 한 번만 처리한다
    @Transactional
    public void deleteProductByEvent(String eventId, ProductDeletedEvent event) {
        if (!processedEvents.markFirst(ProductServiceConsumers.PRODUCT_DELETED, eventId)) {
            return;
        }
        salesPostSyncProductUseCase.deleteProductByUser(event.productId());
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

        List<SyncProduct> updatedProducts;
        try {
            updatedProducts = salesPostApiClient.updateProducts(request, images);
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

        // 복제본 갱신과 판매글 수정 · 완료를 한 트랜잭션으로. 복제본을 못 맞추면 수정 실패로 보고 상품을 복구한다
        // 빠진 재고 알림도 같은 트랜잭션(outbox)에 둔다: 수정이 끝까지 성공했을 때만 나가고, 실패해 복구되면 나가지 않는다
        try {
            transactionTemplate.executeWithoutResult(s -> {
                salesPostSyncProductUseCase.syncProduct(updatedProducts);
                salesPostUpdateUseCase.markCompleted(postId);
                salesPostUpdateUseCase.updateSalesPostByUser(request);
                salesPostRecoveryUseCase.delete(postId);
                publishRemovedInventories(updatedProducts);
            });
        } catch (RuntimeException e) {
            salesPostRecoverUseCase.recover(postId);
            throw e;
        }
    }

    // 수정에서 재고가 빠진 상품마다 InventoryRemovedEvent를 낸다
    private void publishRemovedInventories(List<SyncProduct> updatedProducts) {
        updatedProducts.stream()
                .filter(product -> product.getRemovedInventoryIds() != null && !product.getRemovedInventoryIds().isEmpty())
                .forEach(product -> domainEventPublisher.publish(new InventoryRemovedEvent(
                        product.getSalesPostId(), product.getSellerId(), product.getId(),
                        List.copyOf(product.getRemovedInventoryIds()))));
    }

    // ===== 수정 복구 대사 =====
    public List<Long> findStaleRecoveringPostIds(Long afterId, int limit) {
        return salesPostRecoverUseCase.findStaleRecoveringIds(afterId, limit);
    }

    public List<Long> findStalePendingUpdatePostIds(Long afterId, int limit) {
        return salesPostRecoverUseCase.findStalePendingIds(afterId, limit);
    }

    public void retryRecovering(Long postId) {
        salesPostRecoverUseCase.retryRecovering(postId);
    }

    public void recoverStalePendingUpdate(Long postId) {
        salesPostRecoverUseCase.recoverStalePending(postId);
    }

    // ===== 등록 대사 =====
    // 트랜잭션을 걸지 않는다: 상품 정리(PostApiCallFailedEvent → ProductFacade.rollbackByApiFail)가 사진 파일까지 지우므로,
    // 상품 정리와 판매글 삭제가 각자 커밋돼야 DB 롤백과 파일 삭제가 어긋나지 않는다
    public List<Long> findStaleRegisteringPostIds(int limit) {
        return salesPostCreateUseCase.findStalePendingIds(limit);
    }

    public void cleanUpStaleRegistering(Long postId) {
        salesPostCreateUseCase.cleanUpStalePending(postId);
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
