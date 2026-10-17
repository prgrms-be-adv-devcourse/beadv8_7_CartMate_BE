package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.product.app.TestImageFiles;
import com.cartmate.cartmate.shared.post.event.PostDeletedEvent;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class SalesPostCompensationTest {

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    ApplicationEvents events;

    @MockitoBean
    SalesPostApiClient salesPostApiClient;

    @BeforeEach
    void setUp() {
        salesPostRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 API가 실패하면 글이 지워지고 삭제 이벤트가 발행된다")
    void deletePostWhenProductApiFails() {
        doThrow(new ServiceException(ProductErrorCode.PRODUCT_CREATE_FAILED))
                .when(salesPostApiClient).registerProducts(any(), any());

        List<MultipartFile> images = getDummyImages(TestImageFiles.jpeg(), TestImageFiles.png(), TestImageFiles.png());

        assertThatThrownBy(() -> salesPostFacade.registerSalesPost(request(), images))
                .isInstanceOf(ServiceException.class);

        assertThat(salesPostRepository.count()).isZero();
        assertThat(events.stream(PostDeletedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("상품 API가 성공하면 글이 남고 삭제 이벤트는 발행되지 않는다")
    void keepPostWhenProductApiSucceeds() {
        List<MultipartFile> images = getDummyImages(TestImageFiles.jpeg(), TestImageFiles.png(), TestImageFiles.png());
        salesPostFacade.registerSalesPost(request(), images);

        assertThat(salesPostRepository.count()).isEqualTo(1);
        assertThat(events.stream(PostDeletedEvent.class)).isEmpty();
    }

    private CreateSalesPostRequest request() {
        return new CreateSalesPostRequest(null, 7L, "아이폰 팝니다", "상태 좋아요", List.of());
    }

    private List<MultipartFile> getDummyImages(MultipartFile... files) {
        return List.of(files);
    }
}