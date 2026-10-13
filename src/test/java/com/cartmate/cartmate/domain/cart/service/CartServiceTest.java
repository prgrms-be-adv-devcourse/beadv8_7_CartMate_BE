package com.cartmate.cartmate.domain.cart.service;

import com.cartmate.cartmate.domain.cart.client.CartProductInfo;
import com.cartmate.cartmate.domain.cart.client.ProductClient;
import com.cartmate.cartmate.domain.cart.dto.CartItemResponse;
import com.cartmate.cartmate.domain.cart.dto.CartResponse;
import com.cartmate.cartmate.domain.cart.entity.Cart;
import com.cartmate.cartmate.domain.cart.entity.CartItem;
import com.cartmate.cartmate.domain.cart.repository.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
class CartServiceTest {

    private static final Long MEMBER_ID = 1L;

    private static final CartProductInfo UNIFORM_M = new CartProductInfo(
            1L, 100L, "축구 유니폼", "M / 레드", 39000, "uniform.png", true);
    private static final CartProductInfo SHIN_GUARD = new CartProductInfo(
            5L, 300L, "정강이 보호대", "FREE", 15000, "guard.png", false);

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private CartService cartService;

    @Nested
    @DisplayName("addItem")
    class AddItem {

        @Test
        @DisplayName("장바구니가 없으면 새로 만들어 저장하고 상품을 담는다")
        void createCartWhenAbsent() {
            given(productClient.findByInventoryId(1L)).willReturn(Optional.of(UNIFORM_M));
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());
            given(cartRepository.save(any(Cart.class))).willAnswer(invocation -> invocation.getArgument(0));

            cartService.addItem(MEMBER_ID, 1L, 2);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            then(cartRepository).should().save(captor.capture());
            Cart savedCart = captor.getValue();
            assertThat(savedCart.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(savedCart.getItems()).hasSize(1);
            CartItem item = savedCart.getItems().get(0);
            assertThat(item.getProductId()).isEqualTo(100L);
            assertThat(item.getInventoryId()).isEqualTo(1L);
            assertThat(item.getQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("장바구니가 있으면 기존 장바구니에 담고 새로 저장하지 않는다")
        void useExistingCart() {
            Cart cart = Cart.create(MEMBER_ID);
            given(productClient.findByInventoryId(1L)).willReturn(Optional.of(UNIFORM_M));
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            cartService.addItem(MEMBER_ID, 1L, 2);

            then(cartRepository).should(never()).save(any());
            assertThat(cart.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("같은 상품 옵션을 다시 담으면 수량이 합쳐진다")
        void mergeSameInventory() {
            Cart cart = Cart.create(MEMBER_ID);
            cart.addItem(100L, 1L, 2);
            given(productClient.findByInventoryId(1L)).willReturn(Optional.of(UNIFORM_M));
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            cartService.addItem(MEMBER_ID, 1L, 3);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("존재하지 않는 상품 옵션이면 예외가 발생하고 장바구니는 건드리지 않는다")
        void notFoundInventory() {
            given(productClient.findByInventoryId(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, 999L, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("존재하지 않는 상품 옵션입니다.");
            verifyNoInteractions(cartRepository);
        }

        @Test
        @DisplayName("구매할 수 없는 상품이면 예외가 발생한다")
        void notPurchasable() {
            given(productClient.findByInventoryId(5L)).willReturn(Optional.of(SHIN_GUARD));

            assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, 5L, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("구매할 수 없는 상품입니다.");
            verifyNoInteractions(cartRepository);
        }
    }

    @Nested
    @DisplayName("getCart")
    class GetCart {

        @Test
        @DisplayName("장바구니가 없으면 빈 응답을 반환한다")
        void emptyWhenAbsent() {
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());

            CartResponse response = cartService.getCart(MEMBER_ID);

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

            CartResponse response = cartService.getCart(MEMBER_ID);

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

            CartResponse response = cartService.getCart(MEMBER_ID);

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

    @Nested
    @DisplayName("changeQuantity")
    class ChangeQuantity {

        @Test
        @DisplayName("내 장바구니 항목의 수량을 바꾼다")
        void changeQuantity() {
            Cart cart = cartWithItem(10L, 1L, 2);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            cartService.changeQuantity(MEMBER_ID, 10L, 7);

            assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("내 장바구니에 없는 항목이면 예외가 발생한다")
        void itemNotInMyCart() {
            Cart cart = cartWithItem(10L, 1L, 2);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            assertThatThrownBy(() -> cartService.changeQuantity(MEMBER_ID, 99L, 7))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("장바구니에 없는 상품입니다.");
        }

        @Test
        @DisplayName("장바구니가 없으면 예외가 발생한다")
        void cartNotFound() {
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.changeQuantity(MEMBER_ID, 10L, 7))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("장바구니가 존재하지 않습니다.");
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItem {

        @Test
        @DisplayName("내 장바구니에서 항목을 삭제한다")
        void removeItem() {
            Cart cart = cartWithItem(10L, 1L, 2);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            cartService.removeItem(MEMBER_ID, 10L);

            assertThat(cart.getItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("장바구니의 모든 항목을 비운다")
        void clear() {
            Cart cart = Cart.create(MEMBER_ID);
            cart.addItem(100L, 1L, 2);
            cart.addItem(100L, 2L, 1);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            cartService.clear(MEMBER_ID);

            assertThat(cart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("장바구니가 없어도 예외 없이 끝난다")
        void noCart() {
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());

            assertThatCode(() -> cartService.clear(MEMBER_ID)).doesNotThrowAnyException();
        }
    }

    // DB 없이 테스트하므로 항목 ID를 직접 채워 넣는다
    private Cart cartWithItem(Long cartItemId, Long inventoryId, int quantity) {
        Cart cart = Cart.create(MEMBER_ID);
        CartItem item = cart.addItem(100L, inventoryId, quantity);
        ReflectionTestUtils.setField(item, "id", cartItemId);
        return cart;
    }
}
