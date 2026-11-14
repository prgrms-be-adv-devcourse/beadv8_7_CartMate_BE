package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.code.SalesPostErrorCode;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostRecovery;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostRecoveryRepository;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesPostRecoverUseCase {
    // 즉시 복구(recover)와 겹치지 않게, 복구 중(RECOVERING)이 된 지 이만큼 지난 것만 스케줄러가 다시 시도한다
    static final Duration RECOVERING_STALE_AFTER = Duration.ofMinutes(5);
    // 수정 요청이 끝나기(상품 API 시간 초과 포함)에 충분한 시간. 그보다 오래 수정 중(PENDING)이면 처리 중에 서버가 멈춘 것으로 본다
    static final Duration PENDING_STALE_AFTER = Duration.ofMinutes(10);

    private final SalesPostUpdateUseCase salesPostUpdateUseCase;
    private final SalesPostRecoveryUseCase salesPostRecoveryUseCase;
    private final SalesPostRecoveryRepository salesPostRecoveryRepository;
    private final SalesPostRepository salesPostRepository;
    private final TransactionTemplate transactionTemplate;
    private final SalesPostApiClient salesPostApiClient;
    private final SalesPostSyncProductUseCase salesPostSyncProductUseCase;

    // 수정 복구 대사: 복구 중에 멈춘 판매글 (afterId 다음 ID부터)
    @Transactional(readOnly = true)
    public List<Long> findStaleRecoveringIds(Long afterId, int limit) {
        return findStaleIds(SalesPostUpdateStatus.RECOVERING, RECOVERING_STALE_AFTER, afterId, limit);
    }

    // 수정 복구 대사: 수정 중에 멈춘 판매글 (afterId 다음 ID부터)
    @Transactional(readOnly = true)
    public List<Long> findStalePendingIds(Long afterId, int limit) {
        return findStaleIds(SalesPostUpdateStatus.PENDING, PENDING_STALE_AFTER, afterId, limit);
    }

    private List<Long> findStaleIds(SalesPostUpdateStatus status, Duration staleAfter, Long afterId, int limit) {
        return salesPostRepository.findByUpdateStatusAndUpdateStatusChangedAtBeforeAndIdGreaterThanOrderByIdAsc(
                        status, LocalDateTime.now().minus(staleAfter), afterId, Limit.of(limit)).stream()
                .map(SalesPost::getId)
                .toList();
    }

    // 복구 중에 멈춘 판매글의 복구를 다시 시도한다. 실패하면 재시도 횟수가 오르고 3번째에 RECOVERY_FAILED가 된다(restore)
    public void retryRecovering(Long postId) {
        if (salesPostRecoveryRepository.findBySalesPostId(postId).isEmpty()) {
            // 되돌릴 기록이 없어 다시 시도해도 계속 실패한다. 더 시도하지 않게 실패로 끝낸다
            log.error("복구 기록이 없어 복구 실패로 처리. postId={}", postId);
            transactionTemplate.executeWithoutResult(s -> salesPostUpdateUseCase.markRecoveryFailed(postId));
            return;
        }
        restore(postId);
    }

    // 수정 중에 서버가 멈춰 PENDING으로 남은 판매글을 정리한다
    // - 스냅샷이 있으면 상품 수정이 일어났을 수 있으니 스냅샷으로 복구한다(같은 상태로 되돌리는 거라 수정 전이어도 안전)
    //   복구가 실패하면 예외를 그대로 던져 스케줄러가 실패로 센다(상품 서버 장애 감지). 판매글은 RECOVERING으로 남아 ①에서 다시 시도된다
    // - 스냅샷이 없으면 상품을 건드리기 전에 멈춘 것이라 수정 전 상태로만 되돌리고 복구 기록을 지운다
    public void recoverStalePending(Long postId) {
        SalesPostRecovery recovery = salesPostRecoveryRepository.findBySalesPostId(postId).orElse(null);

        if (recovery != null && recovery.getSnapshot() != null) {
            Boolean moved = transactionTemplate.execute(s -> salesPostUpdateUseCase.markRecovering(postId));
            if (Boolean.TRUE.equals(moved)) {
                restore(postId);
            }
            return;
        }

        // 복구 기록은 PENDING으로 바꿀 때 같은 트랜잭션에서 만들어서 보통은 있다. 없으면 수정 가능한 COMPLETED로 되돌린다
        SalesPostUpdateStatus previous = recovery != null ? recovery.getPreviousStatus() : SalesPostUpdateStatus.COMPLETED;
        if (recovery == null) {
            log.error("수정 중인 판매글에 복구 기록이 없어 COMPLETED로 되돌림. postId={}", postId);
        }
        transactionTemplate.executeWithoutResult(s -> {
            salesPostRecoveryUseCase.delete(postId);
            salesPostUpdateUseCase.revertTo(postId, previous);
        });
    }

    public void recover(Long postId) {
        Boolean moved = transactionTemplate.execute(s -> salesPostUpdateUseCase.markRecovering(postId));
        if (!Boolean.TRUE.equals(moved)) {
            // 스케줄러가 가져간거임
            return;
        }

        try {
            restore(postId);
        } catch (RuntimeException e) {
            log.warn("즉시 복구 실패, 스케줄러가 재시도 예정. postId={}", postId, e);
        }
    }

    public void restore(Long postId) {
        // 1. 복구 기록 조회
        SalesPostRecovery recovery = salesPostRecoveryRepository.findBySalesPostId(postId)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_RECOVERY_NOT_FOUND));

        try {
            // 2. 상품 복구 API 호출 (트랜잭션 밖). 되돌린 상품을 돌려받는다
            List<SyncProduct> restored = salesPostApiClient.restoreProducts(postId, recovery.getSnapshot());

            // 3. 성공: 복제본 되돌리기 + RECOVERED + 복구 기록 삭제 (한 트랜잭션)
            //    복제본을 못 되돌리면 복구 실패로 본다(상품만 되돌아간 채 끝나지 않게)
            transactionTemplate.executeWithoutResult(s -> {
                if (!restored.isEmpty()) {
                    salesPostSyncProductUseCase.syncProduct(restored);
                }
                salesPostUpdateUseCase.markRecovered(postId);
                salesPostRecoveryUseCase.delete(postId);
            });
        } catch (RuntimeException e) {
            // 4. 실패(복구 API · 복제본 되돌리기 모두): 재시도 횟수 +1, 한도 도달 시 RECOVERY_FAILED
            transactionTemplate.executeWithoutResult(s -> handleRestoreFailure(postId));
            throw e;
        }
    }

    private void handleRestoreFailure(Long postId) {
        SalesPostRecovery recovery = salesPostRecoveryRepository.findBySalesPostId(postId).orElseThrow();
        recovery.increaseRetryCount();

        if (recovery.getRetryCount() >= 3) {
            salesPostUpdateUseCase.markRecoveryFailed(postId);
        }
    }
}
