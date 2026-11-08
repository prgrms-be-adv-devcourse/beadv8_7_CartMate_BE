package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSupport;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.post.event.SalesPostDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SalesPostDeleteUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostSupport salesPostSupport;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public SalesPost deleteSalesPostByUser(Long postId, Long sellerId) {
        SalesPost post = salesPostRepository.findById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        if (!post.getSellerId().equals(sellerId)) {
            throw new ServiceException(SalesPostErrorCode.POST_ACCESS_DENIED);
        }

        Long updated = salesPostRepository.startDelete(postId, SalesPostUpdateStatus.editableStatuses());
        if (updated == 0) {
            throw new ServiceException(salesPostSupport.reasonOfBlocked(postId));
        }

        // DELETING 변경과 같은 트랜잭션으로 outbox에 저장한다. 상품 삭제는 product가 이벤트를 받아 처리한다
        eventPublisher.publish(new SalesPostDeletedEvent(postId, sellerId));
        return post;
    }

    // 상품 삭제가 끝난 뒤 판매글 삭제를 완료한다. DELETING일 때만 바꾸고, 이미 끝났거나 없으면 빈 값을 돌려준다(같은 이벤트가 다시 와도 안전)
    @Transactional
    public Optional<SalesPost> completeDelete(Long postId) {
        return salesPostRepository.findById(postId)
                .filter(post -> post.getStatus() == SalesPostSaleStatus.DELETING)
                .map(SalesPost::deleteComplete);
    }

    @Transactional
    public void deleteSalesPostByEvent(Long postId) {
        salesPostRepository.findById(postId)
                .ifPresent(salesPostRepository::delete);
    }
}
