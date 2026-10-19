package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
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
class CartItemUseCaseTest {

    private static final Long MEMBER_ID = 1L;

    private static final CartProductInfo UNIFORM_M = new CartProductInfo(
            1L, 100L, "축구 유니폼", "M / 레드", 39000, "uniform.png", true);
    private static final CartProductInfo SHIN_GUARD = new CartProductInfo(
            5L, 300L, "정강이 보호대", "FREE", 15000, "guard.png", false);

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    private CartItemUseCase cartItemUseCase;

    @BeforeEach
    void setUp() {
        cartItemUseCase = new CartItemUseCase(cartRepository, productClient);
    }

    @Nested
    @DisplayName("addItem")
    class AddItem {

        @Test
        @DisplayName("장바구니가 없으면 새로 만들어 저장하고 상품을 담는다")
        void createCartWhenAbsent() {
            given(productClient.findByInventoryId(1L)).willReturn(Optional.of(UNIFORM_M));
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());
            given(cartRepository.save(any(Cart.class))).willAnswer(invocation -> invocation.getArgument(0));

            cartItemUseCase.addItem(MEMBER_ID, 1L, 2);

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

            cartItemUseCase.addItem(MEMBER_ID, 1L, 2);

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

            cartItemUseCase.addItem(MEMBER_ID, 1L, 3);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("존재하지 않는 상품 옵션이면 예외가 발생하고 장바구니는 건드리지 않는다")
        void notFoundInventory() {
            given(productClient.findByInventoryId(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartItemUseCase.addItem(MEMBER_ID, 999L, 1))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("존재하지 않는 상품 옵션입니다.");
            verifyNoInteractions(cartRepository);
        }

        @Test
        @DisplayName("구매할 수 없는 상품이면 예외가 발생한다")
        void notPurchasable() {
            given(productClient.findByInventoryId(5L)).willReturn(Optional.of(SHIN_GUARD));

            assertThatThrownBy(() -> cartItemUseCase.addItem(MEMBER_ID, 5L, 1))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("구매할 수 없는 상품입니다.");
            verifyNoInteractions(cartRepository);
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

            cartItemUseCase.changeQuantity(MEMBER_ID, 10L, 7);

            assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("내 장바구니에 없는 항목이면 예외가 발생한다")
        void itemNotInMyCart() {
            Cart cart = cartWithItem(10L, 1L, 2);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            assertThatThrownBy(() -> cartItemUseCase.changeQuantity(MEMBER_ID, 99L, 7))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("장바구니에 없는 상품입니다.");
        }

        @Test
        @DisplayName("장바구니가 없으면 예외가 발생한다")
        void cartNotFound() {
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartItemUseCase.changeQuantity(MEMBER_ID, 10L, 7))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("장바구니가 존재하지 않습니다.");
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

            cartItemUseCase.removeItem(MEMBER_ID, 10L);

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

            cartItemUseCase.clear(MEMBER_ID);

            assertThat(cart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("장바구니가 없어도 예외 없이 끝난다")
        void noCart() {
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.empty());

            assertThatCode(() -> cartItemUseCase.clear(MEMBER_ID)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("removeOrderedItems")
    class RemoveOrderedItems {

        @Test
        @DisplayName("개인 주문이 결제되면 주문한 상품 옵션만 장바구니에서 지운다")
        void removePersonalOrderedItems() {
            Cart cart = Cart.create(MEMBER_ID);
            cart.addItem(100L, 1L, 2);
            cart.addItem(100L, 2L, 1);
            cart.addItem(200L, 4L, 1);
            given(cartRepository.findWithItemsByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

            cartItemUseCase.removeOrderedItems(paidEvent("PERSONAL", 1L, 4L));

            assertThat(cart.getItems()).extracting(CartItem::getInventoryId).containsExactly(2L);
        }

        @Test
        @DisplayName("공동구매 상품 주문이 결제되면 장바구니를 건드리지 않는다")
        void ignoreIndividualGroupOrder() {
            cartItemUseCase.removeOrderedItems(paidEvent("INDIVIDUAL_GROUP", 1L));

            verifyNoInteractions(cartRepository);
        }

        private OrderPaidEvent paidEvent(String orderType, Long... inventoryIds) {
            List<OrderPaidEvent.Item> items = Arrays.stream(inventoryIds)
                    .map(inventoryId -> new OrderPaidEvent.Item(null, 100L, inventoryId, null, "상품", null, 1, 10000L))
                    .toList();
            return new OrderPaidEvent(1L, "ORD-1", MEMBER_ID, orderType, null, LocalDateTime.now(), null, items);
        }
    }

    private Cart cartWithItem(Long cartItemId, Long inventoryId, int quantity) {
        Cart cart = Cart.create(MEMBER_ID);
        CartItem item = cart.addItem(100L, inventoryId, quantity);
        ReflectionTestUtils.setField(item, "id", cartItemId);
        return cart;
    }
}
