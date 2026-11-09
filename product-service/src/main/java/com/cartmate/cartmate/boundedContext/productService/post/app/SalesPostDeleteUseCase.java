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
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SalesPostDeleteUseCase {
    // outbox 재시도(최대 15번, 간격 최대 10분)가 끝나는 데 약 1시간 걸린다. 그보다 오래 DELETING이면 멈춘 것으로 본다
    static final Duration STUCK_AFTER = Duration.ofHours(1);

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

    // 삭제 대사: 삭제를 시작한 지 STUCK_AFTER가 지나도록 DELETING인 판매글 ID
    @Transactional(readOnly = true)
    public List<Long> findStuckDeletingIds(int limit) {
        LocalDateTime before = LocalDateTime.now().minus(STUCK_AFTER);
        return salesPostRepository.findByStatusAndModifyDateBeforeOrderByIdAsc(
                        SalesPostSaleStatus.DELETING, before, Limit.of(limit)).stream()
                .map(SalesPost::getId)
                .toList();
    }

    // 삭제 대사: 삭제 이벤트를 다시 발행한다. 이후 단계는 모두 멱등이라 다시 돌려도 안전하다.
    // 삭제 시작 시각을 지금으로 다시 잡아, 원인이 그대로면 STUCK_AFTER 뒤에 다시 시도한다(매 실행마다 쌓이지 않게)
    @Transactional
    public boolean republishDelete(Long postId) {
        if (salesPostRepository.refreshDeleting(postId, LocalDateTime.now()) == 0) {
            return false; // 그 사이 삭제가 끝났다
        }
        SalesPost post = salesPostRepository.findById(postId)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
        eventPublisher.publish(new SalesPostDeletedEvent(postId, post.getSellerId()));
        return true;
    }

    @Transactional
    public void deleteSalesPostByEvent(Long postId) {
        salesPostRepository.findById(postId)
                .ifPresent(salesPostRepository::delete);
    }
}
