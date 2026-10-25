package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostRecovery;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostRecoveryRepository;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSnapshot;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostUpdateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SalesPostRecoveryUseCase {
    private final SalesPostRecoveryRepository salesPostRecoveryRepository;

    public void replace(Long postId, SalesPostUpdateStatus previous) {
        salesPostRecoveryRepository.deleteBySalesPostId(postId);
        salesPostRecoveryRepository.flush();
        salesPostRecoveryRepository.save(new SalesPostRecovery(postId, previous));
    }

    public void saveSnapshot(Long postId, SalesPostSnapshot snapshot) {
        SalesPostRecovery recovery = salesPostRecoveryRepository.findBySalesPostId(postId)
                .orElseThrow(() -> new IllegalStateException("복구 기록이 없습니다. postId=" + postId));
        recovery.saveSnapshot(snapshot);
    }

    public void delete(Long postId) {
        salesPostRecoveryRepository.deleteBySalesPostId(postId);
    }
}
