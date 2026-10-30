package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSellerTrust;
import com.cartmate.cartmate.boundedContext.post.domain.SellerTrustLevel;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostResponse;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerTrustRepository;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import com.cartmate.cartmate.global.page.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesPostSelectUseCaseTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 3, 0);

    private final SalesPostRepository salesPostRepository = mock(SalesPostRepository.class);
    private final SalesPostSellerTrustRepository trustRepository = mock(SalesPostSellerTrustRepository.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final SalesPostSelectUseCase useCase =
            new SalesPostSelectUseCase(salesPostRepository, trustRepository, imageStorage);

    // 상품 목록이 비어 있는 판매글 목(mock)을 만든다. 점수 노출만 검증하므로 상품은 필요 없다.
    private SalesPost post(long postId, long sellerId) {
        SalesPost post = mock(SalesPost.class);
        when(post.getId()).thenReturn(postId);
        when(post.getSellerId()).thenReturn(sellerId);
        when(post.getTitle()).thenReturn("판매글 " + postId);
        when(post.getStatus()).thenReturn(SalesPostSaleStatus.ON_SALE);
        when(post.getProductList()).thenReturn(List.of());
        return post;
    }

    @Test
    @DisplayName("목록에서 판매자의 신뢰도가 있으면 점수와 등급을 붙이고, 없으면 점수 없는 NEW로 응답한다")
    void listAttachesTrust() {
        SalesPost first = post(10L, 1L);
        SalesPost second = post(11L, 2L);
        Pageable pageable = PageRequest.of(0, 10);
        when(salesPostRepository.selectSalesPostAll(any(), any()))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        SalesPostSellerTrust trust = SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, AT);
        when(trustRepository.findAllBySellerIdIn(any())).thenReturn(List.of(trust));

        PageResponse<SelectSalesPostResponse> response = useCase.selectSalesPostAll(new SelectSalesPostRequest(), pageable);

        assertThat(response.content())
                .extracting(SelectSalesPostResponse::getSellerId, SelectSalesPostResponse::getSellerTrustScore,
                        SelectSalesPostResponse::getSellerTrustLevel)
                .containsExactly(tuple(1L, 90, SellerTrustLevel.HIGH), tuple(2L, null, SellerTrustLevel.NEW));
    }

    @Test
    @DisplayName("같은 판매자의 판매글이 여러 개여도 신뢰도는 판매자 수만큼만 한 번에 조회한다")
    void listQueriesTrustOnce() {
        Pageable pageable = PageRequest.of(0, 10);
        // 판매글 목(mock)을 먼저 만든다. thenReturn 인자 안에서 만들면 바깥 when이 끝나기 전에 다른 stubbing이 시작되어 충돌한다.
        List<SalesPost> posts = List.of(post(10L, 1L), post(11L, 1L), post(12L, 2L));
        when(salesPostRepository.selectSalesPostAll(any(), any())).thenReturn(new PageImpl<>(posts, pageable, 3));
        when(trustRepository.findAllBySellerIdIn(any())).thenReturn(List.of(
                SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, AT)));

        PageResponse<SelectSalesPostResponse> response = useCase.selectSalesPostAll(new SelectSalesPostRequest(), pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(trustRepository, times(1)).findAllBySellerIdIn(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
        verify(trustRepository, never()).findById(any());
        assertThat(response.content()).extracting(SelectSalesPostResponse::getSellerTrustLevel)
                .containsExactly(SellerTrustLevel.HIGH, SellerTrustLevel.HIGH, SellerTrustLevel.NEW);
    }

    @Test
    @DisplayName("판매글이 하나도 없는 목록도 정상 응답한다")
    void emptyList() {
        Pageable pageable = PageRequest.of(0, 10);
        when(salesPostRepository.selectSalesPostAll(any(), any())).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(trustRepository.findAllBySellerIdIn(any())).thenReturn(List.of());

        PageResponse<SelectSalesPostResponse> response = useCase.selectSalesPostAll(new SelectSalesPostRequest(), pageable);

        assertThat(response.content()).isEmpty();
    }

    @Test
    @DisplayName("상세에서 판매자의 신뢰도가 있으면 점수와 등급을 붙인다")
    void detailAttachesTrust() {
        SalesPost post = post(10L, 1L);
        when(salesPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(trustRepository.findById(1L)).thenReturn(Optional.of(
                SalesPostSellerTrust.create(1L, 60, SellerTrustLevel.LOW, AT)));

        SelectSalesPostResponse response = useCase.selectSalesPostDetail(10L);

        assertThat(response.getSellerTrustScore()).isEqualTo(60);
        assertThat(response.getSellerTrustLevel()).isEqualTo(SellerTrustLevel.LOW);
    }

    @Test
    @DisplayName("상세에서 판매자의 신뢰도가 없으면 점수 없는 NEW로 응답한다")
    void detailWithoutTrust() {
        SalesPost post = post(10L, 1L);
        when(salesPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(trustRepository.findById(1L)).thenReturn(Optional.empty());

        SelectSalesPostResponse response = useCase.selectSalesPostDetail(10L);

        assertThat(response.getSellerTrustScore()).isNull();
        assertThat(response.getSellerTrustLevel()).isEqualTo(SellerTrustLevel.NEW);
    }
}
