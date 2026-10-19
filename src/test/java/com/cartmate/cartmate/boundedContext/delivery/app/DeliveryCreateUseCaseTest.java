package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand.Item;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand.Option;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItemOption;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemOptionRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryCreateUseCaseTest {

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryItemRepository itemRepository = mock(DeliveryItemRepository.class);
    private final DeliveryItemOptionRepository optionRepository = mock(DeliveryItemOptionRepository.class);
    private DeliveryCreateUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryCreateUseCase(deliveryRepository, itemRepository, optionRepository);
    }

    private DeliveryCreateCommand command(Long sellerId, List<Item> items) {
        return new DeliveryCreateCommand(1L, sellerId, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", items);
    }

    private List<Item> items(long quantity) {
        return List.of(new Item(1000L, "티셔츠", 10_000L, quantity,
                List.of(new Option("사이즈", "L"), new Option("색상", "블랙"))));
    }

    private Delivery saved(DeliveryCreateCommand c, String hash) {
        return Delivery.create(c.memberId(), c.sellerId(), c.orderId(), c.purchaseType(),
                c.receiverName(), c.receiverPhone(), c.zipCode(), c.address(), c.addressDetail(),
                c.idempotencyKey(), hash);
    }

    @Test
    @DisplayName("신규 요청은 요청 해시와 함께 저장한다")
    void createStoresHash() {
        DeliveryCreateCommand c = command(10L, items(2));
        when(deliveryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        useCase.create(c);

        ArgumentCaptor<Delivery> captor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestHash()).isEqualTo(DeliveryRequestHasher.hash(c));
    }

    @Test
    @DisplayName("같은 키에 같은 내용이면 새로 저장하지 않고 성공한다")
    void sameRequestReturns() {
        DeliveryCreateCommand c = command(10L, items(2));
        when(deliveryRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(saved(c, DeliveryRequestHasher.hash(c))));

        useCase.create(c);

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 키에 아이템 수량만 달라도 409 충돌이다")
    void differentItemsConflict() {
        DeliveryCreateCommand original = command(10L, items(2));
        when(deliveryRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(saved(original, DeliveryRequestHasher.hash(original))));

        assertThatThrownBy(() -> useCase.create(command(10L, items(3))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_IDEMPOTENCY_CONFLICT");
    }

    @Test
    @DisplayName("같은 키에 판매자만 달라도 409 충돌이다")
    void differentSellerConflict() {
        DeliveryCreateCommand original = command(10L, items(2));
        when(deliveryRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(saved(original, DeliveryRequestHasher.hash(original))));

        assertThatThrownBy(() -> useCase.create(command(11L, items(2))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_IDEMPOTENCY_CONFLICT");
    }

    @Test
    @DisplayName("아이템을 상품명과 함께 저장하고 옵션은 입력 순서대로 sortOrder를 붙여 저장한다")
    @SuppressWarnings("unchecked")
    void createStoresItemsAndOptionsInOrder() {
        when(deliveryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        useCase.create(command(10L, items(2)));

        ArgumentCaptor<DeliveryItem> itemCaptor = ArgumentCaptor.forClass(DeliveryItem.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProductName()).isEqualTo("티셔츠");
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(2L);

        ArgumentCaptor<List<DeliveryItemOption>> optionCaptor = ArgumentCaptor.forClass(List.class);
        verify(optionRepository).saveAll(optionCaptor.capture());
        assertThat(optionCaptor.getValue())
                .extracting(DeliveryItemOption::getOptionName, DeliveryItemOption::getOptionValue,
                        DeliveryItemOption::getSortOrder)
                .containsExactly(tuple("사이즈", "L", 0), tuple("색상", "블랙", 1));
    }

    @Test
    @DisplayName("옵션이 없거나 null인 아이템은 옵션을 저장하지 않는다")
    void createWithoutOptions() {
        when(deliveryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        useCase.create(command(10L, List.of(
                new Item(1000L, "양말", 10_000L, 1L, List.of()),
                new Item(1001L, "모자", 10_000L, 1L, null))));

        verify(itemRepository, times(2)).save(any(DeliveryItem.class));
        verify(optionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("입력값이 잘못되면 400 오류이고 저장하지 않는다")
    void invalidInputs() {
        assertInvalid(command(null, items(2)), "DELIVERY_SELLER_ID_REQUIRED");
        assertInvalid(command(10L, null), "DELIVERY_ITEMS_REQUIRED");
        assertInvalid(command(10L, List.of()), "DELIVERY_ITEMS_REQUIRED");
        assertInvalid(command(10L, items(0)), "DELIVERY_INVALID_QUANTITY");
        assertInvalid(command(10L, List.of(new Item(1000L, "티셔츠", null, 1L, List.of()))), "DELIVERY_INVALID_UNIT_PRICE");
        assertInvalid(command(10L, List.of(new Item(1000L, "티셔츠", -1L, 1L, List.of()))), "DELIVERY_INVALID_UNIT_PRICE");
        assertInvalid(command(10L, List.of(new Item(null, "티셔츠", 10_000L, 1L, List.of()))),
                "DELIVERY_ORDER_ITEM_ID_REQUIRED");
        assertInvalid(command(10L, List.of(new Item(1000L, "티셔츠", 10_000L, 1L, java.util.Arrays.asList((Option) null)))),
                "DELIVERY_OPTION_CONTAIN_NULL");
        assertInvalid(command(10L, List.of(new Item(1000L, "티셔츠", 10_000L, 1L, List.of(new Option(" ", "L"))))),
                "DELIVERY_OPTION_NAME_REQUIRED");
        assertInvalid(command(10L, List.of(new Item(1000L, "티셔츠", 10_000L, 1L, List.of(new Option("사이즈", null))))),
                "DELIVERY_OPTION_VALUE_REQUIRED");

        verify(deliveryRepository, never()).save(any());
    }

    private void assertInvalid(DeliveryCreateCommand command, String code) {
        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-" + code);
    }
}
