package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SalesPostCreateUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional
    public SalesPost saveSalesPost(CreateSalesPostRequest postDto) {
        return salesPostRepository.save(
                new SalesPost(
                        postDto.getSellerId(),
                        postDto.getTitle(),
                        postDto.getContent(),
                        SalesPostSaleStatus.PENDING,
                        0L,
                        SalesPostUpdateStatus.NEW,
                        LocalDateTime.now()
                ));
    }

    @Transactional
    public void completeSave(Long postId) {
        SalesPost post = salesPostRepository.findById(postId)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
        post.saveComplete();
    }
}
