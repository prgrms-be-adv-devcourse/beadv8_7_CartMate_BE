package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CartQueryUseCaseTest {

    private static final Long MEMBER_ID = 1L;

    private static final CartProductInfo UNIFORM_M = new CartProductInfo(
            1L, 100L, "축구 유니폼", "M / 레드", 39000, "uniform.png", true);
    private static final CartProductInfo SHIN_GUARD = new CartProductInfo(
            5L, 300L, "정강이 보호대", "FREE", 15000, "guard.png", false);

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    private CartQueryUseCase cartQueryUseCase;

    @BeforeEach
    void setUp() {
        cartQueryUseCase = new CartQueryUseCase(new CartSupport(cartRepository), productClient);
    }

    @Nested
    @DisplayName("getCart")
    class GetCart {

        @Test
        @DisplayName("장바구니가 없으면 빈 응답을 반환한다")
        void emptyWhenAbsent() {
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());

            CartResponse response = cartQueryUseCase.getCart(MEMBER_ID);

            assertThat(response.cartId()).isNull();
            assertThat(response.items()).isEmpty();
            assertThat(response.totalPrice()).isZero();
            verifyNoInteractions(productClient);
        }

        @Test
        @DisplayName("상품 정보를 합쳐서 응답하고, 총액은 구매 가능한 상품만 더한다")
        void totalPriceOnlyPurchasable() {
            Cart cart = Cart.create(MEMBER_ID);
            cart.addItem(100L, 1L, 2);
            cart.addItem(300L, 5L, 1);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
            given(productClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(UNIFORM_M, SHIN_GUARD));

            CartResponse response = cartQueryUseCase.getCart(MEMBER_ID);

            assertThat(response.items()).hasSize(2);
            CartItemResponse uniform = findByInventoryId(response, 1L);
            assertThat(uniform.productName()).isEqualTo("축구 유니폼");
            assertThat(uniform.totalPrice()).isEqualTo(78000L);
            assertThat(uniform.purchasable()).isTrue();
            assertThat(findByInventoryId(response, 5L).purchasable()).isFalse();
            assertThat(response.totalPrice()).isEqualTo(78000L);
        }

        @Test
        @DisplayName("상품 정보를 찾을 수 없는 항목은 구매 불가로 표시한다")
        void missingProductInfo() {
            Cart cart = Cart.create(MEMBER_ID);
            cart.addItem(100L, 1L, 2);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
            given(productClient.findAllByInventoryIds(anyCollection())).willReturn(List.of());

            CartResponse response = cartQueryUseCase.getCart(MEMBER_ID);

            CartItemResponse item = findByInventoryId(response, 1L);
            assertThat(item.productName()).isNull();
            assertThat(item.purchasable()).isFalse();
            assertThat(response.totalPrice()).isZero();
        }

        private CartItemResponse findByInventoryId(CartResponse response, Long inventoryId) {
            return response.items().stream()
                    .filter(item -> item.inventoryId().equals(inventoryId))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
