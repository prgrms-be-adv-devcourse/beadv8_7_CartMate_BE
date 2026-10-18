package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand.Item;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand.Option;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        return List.of(new Item(1000L, "티셔츠", quantity,
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
    @DisplayName("해시가 없는 기존 배송은 기본 정보로 비교한다")
    void legacyWithoutHash() {
        DeliveryCreateCommand c = command(10L, items(2));
        when(deliveryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(saved(c, null)));

        useCase.create(c);
        verify(deliveryRepository, never()).save(any());

        assertThatThrownBy(() -> useCase.create(command(11L, items(2))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_IDEMPOTENCY_CONFLICT");
    }
}
