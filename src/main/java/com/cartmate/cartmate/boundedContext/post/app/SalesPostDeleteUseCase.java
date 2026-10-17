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
public class SalesPostDeleteUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional
    public SalesPost deleteSalesPostByUser(Long id) {
        SalesPost post = salesPostRepository.findById(id).orElseThrow(() ->{
            throw new ServiceException(SalesPostErrorCode.POST_NOT_FOUND);
        });
        return post.delete();
    }

    @Transactional
    public void deleteSalesPostByServer(Long postId) {
        salesPostRepository.findById(postId)
                .ifPresent(SalesPost::delete);
    }
}
