package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesPostCreateDraftUseCase {
    private final SalesPostRepository salesPostRepository;

    public SalesPost createSalesPostDraft(CreateSalesPostRequest postDto) {
        return salesPostRepository.save(new SalesPost(postDto));
    }
}
