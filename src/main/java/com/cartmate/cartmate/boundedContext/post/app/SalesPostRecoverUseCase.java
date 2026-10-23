package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostRecovery;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostRecoveryRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesPostRecoverUseCase {
    private final SalesPostUpdateUseCase salesPostUpdateUseCase;
    private final SalesPostRecoveryUseCase salesPostRecoveryUseCase;
    private final SalesPostRecoveryRepository salesPostRecoveryRepository;
    private final TransactionTemplate transactionTemplate;
    private final SalesPostApiClient salesPostApiClient;

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
                .orElseThrow(() -> new IllegalStateException("복구 기록이 없습니다. postId=" + postId));

        try {
            // 2. 상품 복구 API 호출 (트랜잭션 밖)
            salesPostApiClient.restoreProducts(postId, recovery.getSnapshot());
        } catch (RuntimeException e) {
            // 3-1. 실패: 재시도 횟수 +1, 한도 도달 시 RECOVERY_FAILED
            transactionTemplate.executeWithoutResult(s -> handleRestoreFailure(postId));
            throw e;
        }

        // 3-2. 성공: RECOVERED + 복구 기록 삭제 (한 트랜잭션)
        transactionTemplate.executeWithoutResult(s -> {
            salesPostUpdateUseCase.markRecovered(postId);
            salesPostRecoveryUseCase.delete(postId);
        });
    }

    private void handleRestoreFailure(Long postId) {
        SalesPostRecovery recovery = salesPostRecoveryRepository.findBySalesPostId(postId).orElseThrow();
        recovery.increaseRetryCount();

        if (recovery.getRetryCount() >= 3) {
            salesPostUpdateUseCase.markRecoveryFailed(postId);
        }
    }
}
