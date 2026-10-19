package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostResponse;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
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

    @Transactional(readOnly = true)
    public PageResponse<SelectSalesPostResponse> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable) {
        Page<SelectSalesPostResponse> page = salesPostRepository.selectSalesPostAll(request, pageable)
                .map(SalesPost::toSelectResultDto);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public SelectSalesPostResponse selectSalesPostDetail(Long postId) {
        SalesPost post = salesPostRepository.findById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        return post.toSelectResultDto();
    }
}
