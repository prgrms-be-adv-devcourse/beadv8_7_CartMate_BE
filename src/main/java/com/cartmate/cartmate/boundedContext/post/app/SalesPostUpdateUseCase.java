package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SalesPostUpdateUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional(readOnly = true)
    public void validateUpdatable(UpdateSalesPostRequest request) {
        SalesPost post = salesPostRepository.findById(request.getId())
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        if (!post.getSellerId().equals(request.getSellerId())) {
            throw new ServiceException(SalesPostErrorCode.POST_ACCESS_DENIED);
        }
    }

    @Transactional
    public void updateSalesPostByUser(UpdateSalesPostRequest request) {
        SalesPost post = salesPostRepository.findById(request.getId())
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
        post.updateContent(request.getTitle(), request.getContent());
    }
}
