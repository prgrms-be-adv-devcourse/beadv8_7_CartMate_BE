package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesPostWritePostUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional
    public void publishSalesPost(Long postId) {
        SalesPost post = salesPostRepository.findById(postId)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_UPDATE_FAILED));
        post.publish(post);
    }
}
