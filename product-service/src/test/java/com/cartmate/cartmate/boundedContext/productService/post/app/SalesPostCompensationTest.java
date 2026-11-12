package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.global.outbox.QOutboxEvent.outboxEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "cartmate.scheduling.enabled=false",
        // ManualOutboxRelayConfig의 릴레이도 @Scheduled가 붙어 있어 저절로 돈다. 테스트가 직접 돌리도록 첫 실행을 미룬다
        "custom.outbox.relay.initial-delay=PT1H"
})
@ActiveProfiles("test")
@Import(ManualOutboxRelayConfig.class)
class SalesPostCompensationTest {

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    JdbcTemplate jdbcTemplate; // global의 릴레이 잠금 풀기(ManualOutboxRelayConfig)에만 넘긴다
    @Autowired
    JPAQueryFactory queryFactory;
    @Autowired
    TransactionTemplate transactionTemplate;

    // 실제로는 같은 서버의 상품 API를 HTTP로 부르므로 대역으로 바꾼다
    @MockitoBean
    SalesPostApiClient salesPostApiClient;
    @MockitoSpyBean
    ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        salesPostRepository.deleteAll();
        transactionTemplate.executeWithoutResult(status -> queryFactory.delete(outboxEvent).execute());
    }

    @Test
    @DisplayName("상품 API가 실패하면 글이 지워진다")
    void deletePostWhenProductApiFails() {
        doThrow(new ServiceException(ProductErrorCode.PRODUCT_CREATE_FAILED))
                .when(salesPostApiClient).registerProducts(any(), any());

        List<MultipartFile> images = getDummyImages(TestImageFiles.jpeg(), TestImageFiles.png(), TestImageFiles.png());

        assertThatThrownBy(() -> salesPostFacade.registerSalesPost(request(), images))
                .isInstanceOf(ServiceException.class);

        assertThat(salesPostRepository.count()).isZero();
    }

    @Test
    @DisplayName("상품 API가 성공하면 글이 남는다")
    void keepPostWhenProductApiSucceeds() {
        // 상품 API 대역도 실제처럼 저장한 상품을 돌려줘야 post가 복제본을 만들고 등록을 끝낸다
        when(salesPostApiClient.registerProducts(any(), any())).thenAnswer(invocation -> {
            CreateProductRequest productRequest = invocation.getArgument(0);
            LocalDateTime now = LocalDateTime.now();
            return List.of(new SyncProduct(1L, 7L, productRequest.getPostId(), 1L, "아이폰", "설명", 10000L,
                    now, now, List.of(), List.of()));
        });

        List<MultipartFile> images = getDummyImages(TestImageFiles.jpeg(), TestImageFiles.png(), TestImageFiles.png());
        salesPostFacade.registerSalesPost(request(), images);

        assertThat(salesPostRepository.count()).isEqualTo(1);
    }

    // 보상 이벤트는 판매글 삭제와 같은 트랜잭션에서 outbox에 남고, 릴레이가 product에 전달한다.
    // 판매글 id가 그대로 전달돼야 그 글의 상품만 지운다
    @Test
    @DisplayName("상품 API가 실패하면 등록 실패 이벤트가 outbox에 남고, 릴레이되면 product가 같은 판매글 id로 롤백한다")
    void rollbackProductsWithSamePostIdWhenProductApiFails() {
        doThrow(new ServiceException(ProductErrorCode.PRODUCT_CREATE_FAILED))
                .when(salesPostApiClient).registerProducts(any(), any());

        assertThatThrownBy(() -> salesPostFacade.registerSalesPost(request(), getDummyImages(TestImageFiles.jpeg())))
                .isInstanceOf(ServiceException.class);

        List<String> aggregateIds = queryFactory.select(outboxEvent.aggregateId)
                .from(outboxEvent)
                .where(outboxEvent.eventType.eq("SalesPostRegisterFailedEvent"))
                .fetch();
        assertThat(aggregateIds).hasSize(1);
        Long postId = Long.valueOf(aggregateIds.getFirst());

        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();

        verify(productFacade).rollbackByApiFail(anyString(), eq(postId));
    }

    private CreateSalesPostRequest request() {
        return new CreateSalesPostRequest(null, 7L, "아이폰 팝니다", "상태 좋아요", List.of());
    }

    private List<MultipartFile> getDummyImages(MultipartFile... files) {
        return List.of(files);
    }
}
