package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.product.domain.ProductUpdateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
                        SalesPostSaleStatus.PENDING,
                        0L,
                        SalesPostUpdateStatus.NEW,
                        LocalDateTime.now()
                ));
    }
}
