package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.event.PostApiCallFailedEvent;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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
