package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.event.PostApiCallFailedEvent;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
@RecordApplicationEvents
class SalesPostCompensationTest {

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    ApplicationEvents events;

    // 실제로는 같은 서버의 상품 API를 HTTP로 부르므로 대역으로 바꾼다
    @MockitoBean
    SalesPostApiClient salesPostApiClient;
    @MockitoSpyBean
    ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        salesPostRepository.deleteAll();
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

    // 롤백 이벤트(post dto) → product 리스너가 product dto로 바꿔 넘긴다. 판매글 id가 그대로 전달돼야 그 글의 상품만 지운다
    @Test
    @DisplayName("상품 API가 실패하면 롤백 이벤트가 발행되고, product가 같은 판매글 id로 롤백한다")
    void rollbackProductsWithSamePostIdWhenProductApiFails() {
        doThrow(new ServiceException(ProductErrorCode.PRODUCT_CREATE_FAILED))
                .when(salesPostApiClient).registerProducts(any(), any());

        assertThatThrownBy(() -> salesPostFacade.registerSalesPost(request(), getDummyImages(TestImageFiles.jpeg())))
                .isInstanceOf(ServiceException.class);

        List<PostApiCallFailedEvent> failedEvents = events.stream(PostApiCallFailedEvent.class).toList();
        assertThat(failedEvents).hasSize(1);
        Long postId = failedEvents.getFirst().rollbackProductRequest().getPostId();
        assertThat(postId).isNotNull();

        verify(productFacade).rollbackByApiFail(argThat(request -> postId.equals(request.getPostId())));
    }

    private CreateSalesPostRequest request() {
        return new CreateSalesPostRequest(null, 7L, "아이폰 팝니다", "상태 좋아요", List.of());
    }

    private List<MultipartFile> getDummyImages(MultipartFile... files) {
        return List.of(files);
    }
}
