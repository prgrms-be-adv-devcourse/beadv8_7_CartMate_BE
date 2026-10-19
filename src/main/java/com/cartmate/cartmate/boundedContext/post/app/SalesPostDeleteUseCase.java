package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesPostDeleteUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional
    public SalesPost deleteSalesPostByUser(Long postId, Long sellerId) {
        SalesPost post = salesPostRepository.findById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        if (!post.getSellerId().equals(sellerId)) {
            throw new ServiceException(SalesPostErrorCode.POST_ACCESS_DENIED);
        }

        return post.delete();
    }

    @Transactional
    public void deleteSalesPostByEvent(Long postId) {
        salesPostRepository.findById(postId)
                .ifPresent(salesPostRepository::delete);
    }

    public void rollbackSalesPostByProduct(Long postId) {
        salesPostRepository.findById(postId)
                .ifPresent(SalesPost::rollback);
    }
}
