package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.*;
import com.cartmate.cartmate.boundedContext.post.dto.*;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
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

@Service
@RequiredArgsConstructor
public class SalesPostSelectUseCase {
    private final SalesPostRepository salesPostRepository;
    private final ImageStorage imageStorage;

    @Transactional(readOnly = true)
    public PageResponse<SelectSalesPostResponse> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable) {
        Page<SelectSalesPostResponse> page = salesPostRepository.selectSalesPostAll(request, pageable)
                .map(this::toSelectResultDto);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public SelectSalesPostResponse selectSalesPostDetail(Long postId) {
        SalesPost post = salesPostRepository.findById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED && p.getStatus() != SalesPostSaleStatus.SUSPENDED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        return this.toSelectResultDto(post);
    }

    private SelectSalesPostResponse toSelectResultDto(SalesPost post) {
        List<SelectSalesPostProduct> resultList = post.getProductList()
                .stream()
                .map(this::toSelectProductDto)
                .toList();
        return new SelectSalesPostResponse(
                post.getId(),
                post.getSellerId(),
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