package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesPostCreateUseCase {
    private final SalesPostRepository salesPostRepository;

    @Transactional
    public SalesPost saveSalesPost(CreateSalesPostRequest postDto) {
        return salesPostRepository.save(
                new SalesPost(
                        postDto.getSellerId(),
                        postDto.getTitle(),
                        postDto.getContent(),
                        SalesPostSaleStatus.ON_SALE,
                        0L
                ));
    }
}
