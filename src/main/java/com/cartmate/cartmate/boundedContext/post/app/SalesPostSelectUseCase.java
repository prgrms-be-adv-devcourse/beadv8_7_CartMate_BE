package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.*;
import com.cartmate.cartmate.boundedContext.post.dto.*;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerTrustRepository;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesPostSelectUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostSellerTrustRepository salesPostSellerTrustRepository;
    private final ImageStorage imageStorage;

    // 목록은 페이지에 나온 판매자들의 신뢰도를 한 번에 조회해 붙인다.
    @Transactional(readOnly = true)
    public PageResponse<SelectSalesPostResponse> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable) {
        Page<SalesPost> posts = salesPostRepository.selectSalesPostAll(request, pageable);
        Map<Long, SalesPostSellerTrust> trustBySeller = findTrustBySeller(posts.getContent());

        Page<SelectSalesPostResponse> page = posts.map(post ->
                toSelectResultDto(post, trustBySeller.get(post.getSellerId())));
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public SelectSalesPostResponse selectSalesPostDetail(Long postId) {
        SalesPost post = salesPostRepository.findById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED && p.getStatus() != SalesPostSaleStatus.SUSPENDED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        SalesPostSellerTrust trust = salesPostSellerTrustRepository.findById(post.getSellerId()).orElse(null);
        return this.toSelectResultDto(post, trust);
    }

    private Map<Long, SalesPostSellerTrust> findTrustBySeller(List<SalesPost> posts) {
        Set<Long> sellerIds = posts.stream().map(SalesPost::getSellerId).collect(Collectors.toSet());
        return salesPostSellerTrustRepository.findAllBySellerIdIn(sellerIds).stream()
                .collect(Collectors.toMap(SalesPostSellerTrust::getSellerId, Function.identity()));
    }

    // 신뢰도 복제본이 없는 판매자(거래 이력이 아직 없는 판매자)는 점수 없는 NEW로 응답한다.
    private SelectSalesPostResponse toSelectResultDto(SalesPost post, SalesPostSellerTrust trust) {
        List<SelectSalesPostProduct> resultList = post.getProductList()
                .stream()
                .map(this::toSelectProductDto)
                .toList();
        return new SelectSalesPostResponse(
                post.getId(),
                post.getSellerId(),
                trust == null ? null : trust.getScore(),
                trust == null ? SellerTrustLevel.NEW : trust.getLevel(),
                post.getTitle(),
                post.getContent(),
                post.getViewCount(),
                post.getStatus(),
                resultList,
                post.getCreateDate(),
                post.getModifyDate()
        );
    }

    private SelectSalesPostProduct toSelectProductDto(SalesPostProduct product) {
        List<SelectSalesPostProductImage> imageResultList = product.getImageList()
                .stream()
                .map(this::toSelectProductImageDto)
                .toList();
        List<SelectSalesPostProductOption> optionResultList = product.getOptionList()
                .stream()
                .map(this::toSelectProductOptionDto)
                .toList();
        return new SelectSalesPostProduct(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.getStatus(),
                imageResultList,
                optionResultList,
                product.getCreateDate(),
                product.getModifyDate()
        );
    }


    private SelectSalesPostProductImage toSelectProductImageDto(SalesPostProductImage image) {
        return new SelectSalesPostProductImage(
                image.getId(),
                this.imageStorage.toUrl(image.getS3Key()),
                image.getImageSeq(),
                image.getCreateDate(),
                image.getModifyDate()
        );
    }

    private SelectSalesPostProductOption toSelectProductOptionDto(SalesPostProductOption option) {
        return new SelectSalesPostProductOption(
                option.getId(),
                option.getProductTypeId(),
                option.getProductOptionId(),
                option.getCreateDate(),
                option.getModifyDate()
        );
    }
}