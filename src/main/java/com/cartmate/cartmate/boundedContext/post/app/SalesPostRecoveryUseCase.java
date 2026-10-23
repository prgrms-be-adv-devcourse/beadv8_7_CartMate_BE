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

    public void replace(Long postId, SalesPostUpdateStatus previous, SalesPostSnapshot snapshot) {
        salesPostRecoveryRepository.deleteBySalesPostId(postId);
        salesPostRecoveryRepository.flush();
        salesPostRecoveryRepository.save(new SalesPostRecovery(postId, previous, snapshot));
    }

    public void delete(Long postId) {
        salesPostRecoveryRepository.deleteBySalesPostId(postId);
    }
}
