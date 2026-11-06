package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.CartMatchResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.FakeProductClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductSimilarityFinder;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.personal.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.demandPurchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CartMatchQueryUseCaseTest {

    private static final Long MEMBER = 1L;
    private static final Long UNIFORM = 1L;
    private static final Long BALL = 2L;
    private static final Long GUARD = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    private final CartRepository cartRepository = mock(CartRepository.class);
    private final IndividualGroupPurchaseRepository purchaseRepository = mock(IndividualGroupPurchaseRepository.class);

    @Test
    @DisplayName("내 장바구니 상품과 같은 상품으로 열린 어드민 공동구매를 참여 정보와 함께 돌려준다")
    void exactMatch() {
        givenCart(BALL, UNIFORM);
        IndividualGroupPurchase ball = openPurchase(10L, BALL, NOW.plusDays(2));
        IndividualGroupPurchase guard = openPurchase(11L, GUARD, NOW.plusDays(1));
        given(purchaseRepository.findOpenDemandPurchases(NOW)).willReturn(List.of(guard, ball));

        List<CartMatchResponse> matches = useCaseWith(ProductSimilarityResolverTest.EXACT).getMatches(MEMBER, NOW);

        assertThat(matches).hasSize(1);
        CartMatchResponse match = matches.getFirst();
        assertThat(match.individualGroupPurchaseId()).isEqualTo(10L);
        assertThat(match.inviteCode()).isEqualTo(ball.getInviteCode());
        assertThat(match.productId()).isEqualTo(BALL);
        assertThat(match.productName()).isEqualTo("축구공");
        assertThat(match.matchType()).isEqualTo(MatchType.EXACT);
        assertThat(match.similarity()).isEqualTo(1.0);
        assertThat(match.matchedCartProductIds()).containsExactly(BALL);
        assertThat(match.paybackMinQuantity()).isEqualTo(10);
        assertThat(match.paybackAmountPerUnit()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("유사 매칭이 섞이면 유사도 높은 순, 같으면 마감 임박 순이고, 한 공동구매에 맞은 내 상품을 모두 보여준다")
    void similarAndOrder() {
        Long otherBall = 50L;
        givenCart(UNIFORM, otherBall);
        IndividualGroupPurchase ball = openPurchase(10L, BALL, NOW.plusDays(2));
        IndividualGroupPurchase uniform = openPurchase(11L, UNIFORM, NOW.plusDays(3));
        given(purchaseRepository.findOpenDemandPurchases(NOW)).willReturn(List.of(ball, uniform));
        ProductSimilarityFinder vector = (sources, candidates) -> Map.of(
                UNIFORM, List.of(new ProductSimilarity(BALL, 0.86, MatchType.VECTOR)),
                otherBall, List.of(new ProductSimilarity(BALL, 0.91, MatchType.VECTOR))
        );

        List<CartMatchResponse> matches =
                useCaseWith(ProductSimilarityResolverTest.EXACT, vector).getMatches(MEMBER, NOW);

        assertThat(matches)
                .extracting(CartMatchResponse::individualGroupPurchaseId, CartMatchResponse::matchType,
                        CartMatchResponse::similarity)
                .containsExactly(tuple(11L, MatchType.EXACT, 1.0), tuple(10L, MatchType.VECTOR, 0.91));
        assertThat(matches.get(1).matchedCartProductIds()).containsExactly(UNIFORM, otherBall);
    }

    @Test
    @DisplayName("장바구니가 없거나 비어 있으면 공동구매를 조회하지 않는다")
    void emptyCart() {
        given(cartRepository.findWithItemsByMemberId(MEMBER)).willReturn(Optional.empty());

        assertThat(useCaseWith(ProductSimilarityResolverTest.EXACT).getMatches(MEMBER, NOW)).isEmpty();
        verify(purchaseRepository, never()).findOpenDemandPurchases(any());
    }

    @Test
    @DisplayName("열린 어드민 공동구매가 없거나 맞는 상품이 없으면 빈 목록이다")
    void noMatch() {
        givenCart(UNIFORM);
        given(purchaseRepository.findOpenDemandPurchases(NOW))
                .willReturn(List.of(), List.of(openPurchase(10L, BALL, NOW.plusDays(1))));
        CartMatchQueryUseCase useCase = useCaseWith(ProductSimilarityResolverTest.EXACT);

        assertThat(useCase.getMatches(MEMBER, NOW)).isEmpty();
        assertThat(useCase.getMatches(MEMBER, NOW)).isEmpty();
    }

    @Test
    @DisplayName("공동구매 상품이 판매 불가로 바뀌었거나 상품 정보를 찾지 못하면 매칭에서 뺀다")
    void excludeNotPurchasable() {
        Long unknown = 999L;
        givenCart(GUARD, unknown);
        given(purchaseRepository.findOpenDemandPurchases(NOW)).willReturn(List.of(
                openPurchase(11L, GUARD, NOW.plusDays(1)),
                openPurchase(12L, unknown, NOW.plusDays(1))));

        assertThat(useCaseWith(ProductSimilarityResolverTest.EXACT).getMatches(MEMBER, NOW)).isEmpty();
    }

    @Test
    @DisplayName("같은 상품의 열린 공동구매가 어긋나게 둘 이상이면 마감이 가장 가까운 것 하나만 보여준다")
    void duplicateOpenPurchase() {
        givenCart(BALL);
        given(purchaseRepository.findOpenDemandPurchases(NOW)).willReturn(List.of(
                openPurchase(20L, BALL, NOW.plusDays(1)),
                openPurchase(21L, BALL, NOW.plusDays(2))));

        assertThat(useCaseWith(ProductSimilarityResolverTest.EXACT).getMatches(MEMBER, NOW))
                .extracting(CartMatchResponse::individualGroupPurchaseId)
                .containsExactly(20L);
    }

    private void givenCart(Long... productIds) {
        Cart cart = Cart.create(MEMBER);
        for (Long productId : productIds) {
            cart.addItem(productId, productId, 1);
        }
        given(cartRepository.findWithItemsByMemberId(MEMBER)).willReturn(Optional.of(cart));
    }

    private static IndividualGroupPurchase openPurchase(Long id, Long productId, LocalDateTime deadlineAt) {
        IndividualGroupPurchase purchase = demandPurchase(99L, "invite%06d".formatted(id), productId, deadlineAt,
                NOW.minusHours(1));
        ReflectionTestUtils.setField(purchase, "id", id);
        return purchase;
    }

    private CartMatchQueryUseCase useCaseWith(ProductSimilarityFinder... finders) {
        return new CartMatchQueryUseCase(cartRepository, purchaseRepository,
                new ProductSimilarityResolver(List.of(finders)), new FakeProductClient());
    }
}
