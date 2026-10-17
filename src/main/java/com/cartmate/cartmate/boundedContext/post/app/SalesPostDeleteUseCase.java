package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.shared.post.dto.SalesPostRollbackRequest;
import com.cartmate.cartmate.shared.post.event.PostDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesPostDeleteUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional
    public void deleteSalesPostByUser(Long postId) {
        SalesPost post = salesPostRepository.findById(postId).orElseThrow(
                () -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND)
        );

        post.delete();

        GlobalConfig.getEventPublisher().publish(
                new PostDeletedEvent(
                        new SalesPostRollbackRequest(postId)
                )
        );
    }

    @Transactional
    public void deleteSalesPostByEvent(Long postId) {
        salesPostRepository.findById(postId)
                .ifPresent(salesPostRepository::delete);
    }
}
