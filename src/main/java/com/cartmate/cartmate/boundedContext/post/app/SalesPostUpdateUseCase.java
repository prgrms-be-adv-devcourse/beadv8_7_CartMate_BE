package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSnapshot;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSupport;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesPostUpdateUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostSupport salesPostSupport;

    @Transactional
    public void updateSalesPostByUser(UpdateSalesPostRequest request) {
        SalesPost post = salesPostRepository.findById(request.getId())
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
        post.updateContent(request.getTitle(), request.getContent());
    }

    public SalesPostUpdateStatus markPending(SalesPost post) {
        SalesPostUpdateStatus previous = post.getUpdateStatus();
        Long updated = salesPostRepository.startUpdate(
                post.getId(), SalesPostUpdateStatus.editableStatuses(), LocalDateTime.now()
        );
        if (updated == 0) {
            throw new ServiceException(salesPostSupport.reasonOfBlocked(post.getId()));
        }
        return previous;
    }

    public void revertTo(Long postId, SalesPostUpdateStatus previous) {
        long updated = salesPostRepository.changeUpdateStatus(
                postId, List.of(SalesPostUpdateStatus.PENDING), previous, LocalDateTime.now());

        if (updated == 0) {
            log.warn("이전 상태 복원 실패 - 이미 다른 상태로 변경됨. postId={}", postId);
        }
    }

    public Boolean markRecovering(Long postId) {
        return salesPostRepository.changeUpdateStatus(
                postId,
                List.of(SalesPostUpdateStatus.PENDING),
                SalesPostUpdateStatus.RECOVERING,
                LocalDateTime.now()
        ) == 1;
    }

    public void markRecovered(Long postId) {
        long updated = salesPostRepository.changeUpdateStatus(
                postId,
                List.of(SalesPostUpdateStatus.RECOVERING),
                SalesPostUpdateStatus.RECOVERED,
                LocalDateTime.now()
        );

        if (updated == 0) {
            throw new ServiceException(SalesPostErrorCode.POST_NOT_RECOVERING_STATUS);
        }
    }

    public void markRecoveryFailed(Long postId) {
        long updated = salesPostRepository.changeUpdateStatus(
                postId,
                List.of(SalesPostUpdateStatus.RECOVERING),
                SalesPostUpdateStatus.RECOVERY_FAILED,
                LocalDateTime.now()
        );

        if (updated == 0) {
            log.warn("복구 실패 상태로 변경하지 못함 - 이미 다른 상태. postId={}", postId);
        }
    }

    public void markCompleted(Long postId) {
        long updated = salesPostRepository.changeUpdateStatus(
                postId,
                List.of(SalesPostUpdateStatus.PENDING),
                SalesPostUpdateStatus.COMPLETED,
                LocalDateTime.now()
        );

        if (updated == 0) {
            throw new IllegalStateException("이미 복구 처리된 수정입니다. postId=" + postId);
        }
    }
}
