package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.*;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostCountResponse;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostDetailResponse;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostDetailResponse.AdminSalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostDetailResponse.AdminSalesPostSanction;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostSummaryResponse;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSanctionRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerRepository;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminSalesPostUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostSellerRepository salesPostSellerRepository;
    private final SalesPostSanctionRepository salesPostSanctionRepository;
    private final SalesPostApiClient salesPostApiClient;
    private final SalesPostHideUseCase salesPostHideUseCase;
    private final ImageStorage imageStorage;

    @Transactional(readOnly = true)
    public PageResponse<AdminSalesPostSummaryResponse> getList(SalesPostSaleStatus status, String keyword, Pageable pageable) {
        Page<SalesPost> page = salesPostRepository.selectForAdmin(status, keyword, pageable);

        List<Long> sellerIds = page.getContent().stream().map(SalesPost::getSellerId).distinct().toList();
        Map<Long, SalesPostSeller> sellers = salesPostSellerRepository.findAllById(sellerIds).stream()
                .collect(Collectors.toMap(SalesPostSeller::getId, Function.identity()));

        return PageResponse.of(page.map(post -> toSummary(post, sellers.get(post.getSellerId()))));
    }

    @Transactional(readOnly = true)
    public AdminSalesPostCountResponse getCounts() {
        long onSale = salesPostRepository.countByStatus(SalesPostSaleStatus.ON_SALE);
        long paused = salesPostRepository.countByStatus(SalesPostSaleStatus.PAUSED);
        long suspended = salesPostRepository.countByStatus(SalesPostSaleStatus.SUSPENDED);
        long closed = salesPostRepository.countByStatus(SalesPostSaleStatus.CLOSED);
        return new AdminSalesPostCountResponse(onSale + paused + suspended + closed, onSale, paused, suspended);
    }

    @Transactional(readOnly = true)
    public AdminSalesPostDetailResponse getDetail(Long postId) {
        SalesPost post = findVisiblePost(postId);
        SalesPostSeller seller = salesPostSellerRepository.findById(post.getSellerId()).orElse(null);

        List<AdminSalesPostProduct> products = post.getProductList().stream()
                .map(p -> new AdminSalesPostProduct(
                        p.getId(), p.getName(), p.getBasePrice(), p.getOptionList().size(),
                        p.getImageList().stream()
                                .sorted(Comparator.comparingInt(SalesPostProductImage::getImageSeq))
                                .map(img -> imageStorage.toUrl(img.getS3Key()))
                                .toList()))
                .toList();

        List<AdminSalesPostSanction> sanctions = salesPostSanctionRepository.findBySalesPostIdOrderByIdDesc(postId).stream()
                .map(s -> new AdminSalesPostSanction(s.getId(), s.getAdminId(), s.getAction(), s.getReason(), s.getCreateDate()))
                .toList();

        return new AdminSalesPostDetailResponse(
                post.getId(), post.getTitle(), post.getContent(),
                post.getSellerId(), sellerName(seller),
                post.getStatus(), post.getViewCount(), post.getCreateDate(),
                products, sanctions);
    }

    // 관리자 숨김: 상품 API 먼저(새 구매 차단) → 판매글 숨김 + 이력 + 진행 중 거래 정리 이벤트
    @Transactional
    public void suspend(Long postId, Long adminId, String reason) {
        SalesPost post = findVisiblePost(postId);
        post.validateSuspendable();

        salesPostApiClient.changeProductStatus(postId, ProductStatus.SUSPENDED);

        post.suspendByAdmin();
        String trimmed = reason.trim();
        salesPostSanctionRepository.save(new SalesPostSanction(postId, adminId, SalesPostSanctionAction.SUSPEND, trimmed));
        salesPostHideUseCase.publishHidden(post, SalesPostHideType.ADMIN_SUSPENDED, adminId, trimmed);
    }

    // 숨김 해제. 숨김 중 취소된 거래는 되돌리지 않는다.
    @Transactional
    public void resume(Long postId, Long adminId, String reason) {
        SalesPost post = findVisiblePost(postId);
        post.validateResumable();

        salesPostApiClient.changeProductStatus(postId, ProductStatus.ON_SALE);

        post.resumeByAdmin();
        salesPostSanctionRepository.save(new SalesPostSanction(postId, adminId, SalesPostSanctionAction.RESUME, reason.trim()));
    }

    private SalesPost findVisiblePost(Long postId) {
        return salesPostRepository.findById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
    }

    private AdminSalesPostSummaryResponse toSummary(SalesPost post, SalesPostSeller seller) {
        List<SalesPostProduct> products = post.getProductList();
        Long minPrice = products.stream().map(SalesPostProduct::getBasePrice).filter(Objects::nonNull)
                .min(Long::compareTo).orElse(null);
        String thumbnail = products.stream()
                .flatMap(p -> p.getImageList().stream())
                .min(Comparator.comparingInt(SalesPostProductImage::getImageSeq))
                .map(img -> imageStorage.toUrl(img.getS3Key()))
                .orElse(null);

        return new AdminSalesPostSummaryResponse(
                post.getId(), post.getTitle(), post.getSellerId(), sellerName(seller),
                thumbnail, minPrice, products.size(),
                post.getStatus(), post.getViewCount(), post.getCreateDate());
    }

    private String sellerName(SalesPostSeller seller) {
        if (seller == null) return null;
        if (seller.getBusinessName() != null && !seller.getBusinessName().isBlank()) return seller.getBusinessName();
        return seller.getRepresentativeName();
    }
}