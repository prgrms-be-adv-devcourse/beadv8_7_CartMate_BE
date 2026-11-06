package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostRecovery;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostRecoveryRepository;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSnapshot;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_RECOVERY_NOT_FOUND));
        recovery.saveSnapshot(snapshot);
    }

    public void delete(Long postId) {
        salesPostRecoveryRepository.deleteBySalesPostId(postId);
    }
}
