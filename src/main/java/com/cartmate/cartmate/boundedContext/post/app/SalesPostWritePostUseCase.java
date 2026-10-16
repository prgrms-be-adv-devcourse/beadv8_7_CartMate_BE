package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.cartmate.cartmate.global.code.ErrorCode.POST_UPDATE_FAILED;

@Service
@RequiredArgsConstructor
public class SalesPostWritePostUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional
    public void publishSalesPost(CreateSalesPostRequest postDto) {
        SalesPost post = salesPostRepository.findById(postDto.getId())
                .orElseThrow(() -> new DomainException(POST_UPDATE_FAILED));
        post.publish(post);
    }
}
