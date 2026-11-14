package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.post.event.SalesPostRegisterFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostCreateUseCase {
    private final SalesPostRepository salesPostRepository;
    private final TransactionTemplate transactionTemplate;
    private final DomainEventPublisher domainEventPublisher;

    static final Duration REGISTER_STALE_AFTER = Duration.ofMinutes(30);

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

    @Transactional
    public void completeSave(Long postId) {
        SalesPost post = salesPostRepository.findById(postId)
                .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
        post.saveComplete();
    }

    // 등록 대사: 등록 중(PENDING)으로 오래 남은 판매글
    @Transactional(readOnly = true)
    public List<Long> findStalePendingIds(int limit) {
        LocalDateTime before = LocalDateTime.now().minus(REGISTER_STALE_AFTER);
        return salesPostRepository.findByStatusAndCreateDateBeforeOrderByIdAsc(
                        SalesPostSaleStatus.PENDING, before, Limit.of(limit)).stream()
                .map(SalesPost::getId)
                .toList();
    }

    // 등록 중에 서버가 멈춰 PENDING으로 남은 판매글을 등록 실패 보상과 같이 정리한다.
    // 상품을 먼저 지우고 판매글을 나중에 지운다. 상품 정리가 실패해도 판매글이 PENDING으로 남아 다음 실행에서 다시 시도된다
    public void cleanUpStalePending(Long postId) {
        // 1. 그 사이 등록이 끝났으면(ON_SALE) 건드리지 않는다
        if (!isPending(postId)) {
            return;
        }

        // 2. 상품 · 사진 행 삭제 + 사진 파일 삭제 (등록 실패 보상과 같은 이벤트)
        transactionTemplate.executeWithoutResult(s ->
                domainEventPublisher.publish(new SalesPostRegisterFailedEvent(postId)));

        // 3. 판매글 삭제 (복제본은 cascade로 함께). 아직 PENDING일 때만
        transactionTemplate.executeWithoutResult(s ->
                salesPostRepository.findLatestById(postId)
                        .filter(post -> post.getStatus() == SalesPostSaleStatus.PENDING)
                        .ifPresent(salesPostRepository::delete));
    }

    private boolean isPending(Long postId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(s ->
                salesPostRepository.findLatestById(postId)
                        .map(post -> post.getStatus() == SalesPostSaleStatus.PENDING)
                        .orElse(false)));
    }
}
