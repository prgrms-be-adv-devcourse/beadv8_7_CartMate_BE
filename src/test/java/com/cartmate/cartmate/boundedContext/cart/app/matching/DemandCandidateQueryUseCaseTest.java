package com.cartmate.cartmate.boundedContext.cart.app.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductDemand;
import com.cartmate.cartmate.boundedContext.cart.dto.matching.DemandCandidateResponse;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.FakeProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.cart.out.matching.CartDemandReader;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DemandCandidateQueryUseCaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    private final List<LocalDateTime> requestedSince = new ArrayList<>();
    private final List<Collection<Long>> requestedExcluded = new ArrayList<>();
    private final IndividualGroupPurchaseRepository purchaseRepository = mock(IndividualGroupPurchaseRepository.class);

    @Test
    @DisplayName("최근 N일 기준 시각과 최소 인원으로 수요를 조회하고, 상품 정보를 붙여 많이 모인 순으로 돌려준다")
    void candidates() {
        DemandCandidateQueryUseCase useCase = useCaseWith(List.of(
                new ProductDemand(1L, Set.of(1L, 2L, 3L, 4L, 5L)),
                new ProductDemand(2L, Set.of(1L, 2L, 3L, 4L, 5L, 6L))
        ));

        List<DemandCandidateResponse> candidates = useCase.getCandidates(5, 30, 20, 14, NOW);

        assertThat(requestedSince).containsExactly(NOW.minusDays(30));
        assertThat(candidates).extracting(DemandCandidateResponse::productId).containsExactly(2L, 1L);
        DemandCandidateResponse first = candidates.getFirst();
        assertThat(first.productName()).isEqualTo("축구공");
        assertThat(first.purchasable()).isTrue();
        assertThat(first.exactMemberCount()).isEqualTo(6);
        assertThat(first.similarMemberCount()).isZero();
        assertThat(first.totalMemberCount()).isEqualTo(6);
        assertThat(first.similarProducts()).isEmpty();
    }

    @Test
    @DisplayName("조회 개수만큼만 돌려준다")
    void limitSize() {
        DemandCandidateQueryUseCase useCase = useCaseWith(List.of(
                new ProductDemand(1L, Set.of(1L, 2L, 3L)),
                new ProductDemand(2L, Set.of(1L, 2L))
        ));

        assertThat(useCase.getCandidates(1, 30, 1, 14, NOW))
                .extracting(DemandCandidateResponse::productId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("상품 정보를 찾지 못한 후보는 이름 없이 구매 불가로 표시한다")
    void unknownProduct() {
        DemandCandidateQueryUseCase useCase = useCaseWith(List.of(
                new ProductDemand(999L, Set.of(1L, 2L))
        ));

        DemandCandidateResponse candidate = useCase.getCandidates(2, 30, 20, 14, NOW).getFirst();

        assertThat(candidate.productName()).isNull();
        assertThat(candidate.purchasable()).isFalse();
    }

    @Test
    @DisplayName("판매 불가 상품은 구매 불가로 표시한다")
    void notPurchasable() {
        DemandCandidateQueryUseCase useCase = useCaseWith(List.of(
                new ProductDemand(3L, Set.of(1L, 2L))
        ));

        assertThat(useCase.getCandidates(2, 30, 20, 14, NOW).getFirst().purchasable()).isFalse();
    }

    @Test
    @DisplayName("수요가 없으면 상품을 조회하지 않고 빈 목록이다")
    void empty() {
        assertThat(useCaseWith(List.of()).getCandidates(5, 30, 20, 14, NOW)).isEmpty();
    }

    @Test
    @DisplayName("최소 인원 · 기간 · 조회 개수 범위를 벗어나면 거절한다")
    void validate() {
        DemandCandidateQueryUseCase useCase = useCaseWith(List.of());

        assertThatThrownBy(() -> useCase.getCandidates(0, 30, 20, 14, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_MIN_MEMBERS.getMsg());
        assertThatThrownBy(() -> useCase.getCandidates(5, 91, 20, 14, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_DAYS.getMsg());
        assertThatThrownBy(() -> useCase.getCandidates(5, 30, 101, 14, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_SIZE.getMsg());
        assertThatThrownBy(() -> useCase.getCandidates(5, 30, 20, 91, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_COOLDOWN.getMsg());
    }

    @Test
    @DisplayName("최근 재개설 제한 기간 안에 끝난 수요 공동구매 상품은 집계에서 빼고, 이미 열린 공동구매 ID를 표시한다")
    void cooldownAndOpenPurchase() {
        given(purchaseRepository.findDemandProductIdsEndedBetween(NOW.minusDays(14), NOW)).willReturn(List.of(7L));
        given(purchaseRepository.findOpenDemandPurchaseIds(List.of(2L, 1L), NOW)).willReturn(Map.of(2L, 55L));
        DemandCandidateQueryUseCase useCase = useCaseWith(List.of(
                new ProductDemand(1L, Set.of(1L, 2L, 3L)),
                new ProductDemand(2L, Set.of(1L, 2L, 3L, 4L))
        ));

        List<DemandCandidateResponse> candidates = useCase.getCandidates(3, 30, 20, 14, NOW);

        assertThat(requestedExcluded).containsExactly(List.of(7L));
        assertThat(candidates).extracting(DemandCandidateResponse::productId, DemandCandidateResponse::openGroupPurchaseId)
                .containsExactly(tuple(2L, 55L), tuple(1L, null));
    }

    @Test
    @DisplayName("재개설 제한 기간이 0이면 끝난 공동구매를 조회하지 않는다")
    void noCooldown() {
        DemandCandidateQueryUseCase useCase = useCaseWith(List.of());

        useCase.getCandidates(3, 30, 20, 0, NOW);

        assertThat(requestedExcluded).containsExactly(List.of());
        verify(purchaseRepository, never()).findDemandProductIdsEndedBetween(any(), any());
    }

    private DemandCandidateQueryUseCase useCaseWith(List<ProductDemand> demands) {
        CartDemandReader reader = (since, minMemberCount, limit, excludedProductIds) -> {
            requestedSince.add(since);
            requestedExcluded.add(excludedProductIds);
            return demands;
        };
        DemandAggregator aggregator = new DemandAggregator(
                new ProductSimilarityResolver(List.of(ProductSimilarityResolverTest.EXACT)));
        return new DemandCandidateQueryUseCase(reader, aggregator, new FakeProductClient(), purchaseRepository);
    }
}
