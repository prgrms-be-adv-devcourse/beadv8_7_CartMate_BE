package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SalesPostSupport {
    private final SalesPostRepository salesPostRepository;

    @Transactional(readOnly = true)
    public SalesPost validateUpdatable(UpdateSalesPostRequest request) {
        SalesPost post = salesPostRepository.findById(request.getId())
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        if (!post.getSellerId().equals(request.getSellerId())) { // 로그인한 사용자의 sellerId와 비교하도록 수정
            throw new ServiceException(SalesPostErrorCode.POST_ACCESS_DENIED);
        }

        if (post.getStatus() == SalesPostSaleStatus.PENDING) { // 등록 중이거나 삭제 중인 글
            throw new ServiceException(SalesPostErrorCode.POST_SALE_PENDING_STATUS);
        }

        if (request.getTitle() == null || request.getContent() == null) {
            throw new ServiceException(SalesPostErrorCode.POST_INVALID_INPUT);
        }

        return post;
    }

    @Transactional(readOnly = true)
    public SalesPostErrorCode reasonOfBlocked(Long postId) {
        return salesPostRepository.findLatestById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .map(this::reasonOf)
                .orElse(SalesPostErrorCode.POST_NOT_FOUND);
    }

    private SalesPostErrorCode reasonOf(SalesPost current) {
        if (current.getStatus() == SalesPostSaleStatus.PENDING) {
            return SalesPostErrorCode.POST_SALE_PENDING_STATUS;
        }
        return switch (current.getUpdateStatus()) {
            case PENDING -> SalesPostErrorCode.POST_PENDING_STATUS;
            case RECOVERING -> SalesPostErrorCode.POST_RECOVERING_STATUS;
            default -> SalesPostErrorCode.POST_UPDATE_FAILED;
        };
    }
}
