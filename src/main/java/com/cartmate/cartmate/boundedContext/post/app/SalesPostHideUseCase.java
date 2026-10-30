package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 내림·재개, 그리고 판매글이 내려갔을 때의 이벤트 발행을 한곳에서 처리한다.
 * (관리자 숨김은 AdminSalesPostUseCase, 삭제는 SalesPostDeleteUseCase가 publishHidden을 호출)
 */
@Service
@RequiredArgsConstructor
public class SalesPostHideUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostProductRepository salesPostProductRepository;
    private final SalesPostApiClient salesPostApiClient;
    private final SalesPostHidePolicy hidePolicy;
    private final EventPublisher eventPublisher;

    // 판매자 내림. 상품 API 먼저 → 성공하면 판매글 변경  이벤트 (판매글 수정과 같은 순서 원칙)
    @Transactional
    public void pauseBySeller(Long postId, Long sellerId, Long actorMemberId) {
        SalesPost post = findOwnedPost(postId, sellerId);
        post.validatePausable();

        salesPostApiClient.changeProductStatus(postId, ProductStatus.PAUSED);

        post.pauseBySeller();
        publishHidden(post, SalesPostHideType.SELLER_PAUSED, actorMemberId, null);
    }

    // 판매자 재개. 내림 중에 취소된 거래는 되돌리지 않는다.
    @Transactional
    public void resumeBySeller(Long postId, Long sellerId) {
        SalesPost post = findOwnedPost(postId, sellerId);
        post.validateResumableBySeller();

        salesPostApiClient.changeProductStatus(postId, ProductStatus.ON_SALE);

        post.resumeBySeller();
    }

    // 판매글 상태를 바꾼 트랜잭션 안에서 호출한다. 받는 쪽은 커밋 후에 처리한다.
    public void publishHidden(SalesPost post, SalesPostHideType hideType, Long actorId, String reason) {
        publishHidden(post, salesPostProductRepository.findIdsBySalesPostId(post.getId()), hideType, actorId, reason);
    }

    // 삭제처럼 상품 복제본이 먼저 지워질 수 있는 경우, 미리 모아 둔 상품 ID로 발행한다.
    public void publishHidden(SalesPost post, List<Long> productIds, SalesPostHideType hideType, Long actorId, String reason) {
        if (!hidePolicy.settleImmediately(hideType, reason)) {
            return; // 승인 절차는 아직 없음. 정책이 바뀌면 여기서 승인 대기로 보낸다.
        }
        eventPublisher.publish(new SalesPostHiddenEvent(
                post.getId(), post.getSellerId(), List.copyOf(productIds), hideType, actorId, reason, LocalDateTime.now()));
    }

    private SalesPost findOwnedPost(Long postId, Long sellerId) {
        SalesPost post = salesPostRepository.findById(postId)
                .filter(p -> p.getStatus() != SalesPostSaleStatus.DELETED)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
        if (!post.getSellerId().equals(sellerId)) {
            throw new ServiceException(SalesPostErrorCode.POST_ACCESS_DENIED);
        }
        return post;
    }

    // 최근 24시간 안에 내려간 판매글의 숨김 이벤트를 다시 발행한다 (대사).
    // 후처리는 멱등이라 이미 정리된 건은 건너뛰고 놓친 것만 처리된다.
    // 리스너가 커밋 후에 실행되므로 반드시 트랜잭션 안에서 발행한다.
    @Transactional
    public int republishRecentlyHidden() {
        List<SalesPost> posts = salesPostRepository.findByStatusInAndHiddenAtAfter(
                List.of(SalesPostSaleStatus.PAUSED, SalesPostSaleStatus.SUSPENDED, SalesPostSaleStatus.DELETED),
                LocalDateTime.now().minusHours(24));
        for (SalesPost post : posts) {
            publishHidden(post, toHideType(post.getStatus()), null, "대사 재처리");
        }
        return posts.size();
    }

    private SalesPostHideType toHideType(SalesPostSaleStatus status) {
        return switch (status) {
            case PAUSED -> SalesPostHideType.SELLER_PAUSED;
            case SUSPENDED -> SalesPostHideType.ADMIN_SUSPENDED;
            default -> SalesPostHideType.SELLER_DELETED;
        };
    }
}