package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryRetryServiceTest {

    private final DeliveryFacade deliveryFacade = mock(DeliveryFacade.class);
    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private DeliveryRetryService retryService;

    private final DeliveryCreateCommand command = new DeliveryCreateCommand(
            1L, 10L, 100L, "20261115-AB3K9X2P", PurchaseType.INDIVIDUAL_GROUP, "홍길동", "010-0000-0000",
            "12345", "서울", "101호", "key-1",
            List.of(new DeliveryCreateCommand.Item(1000L, "티셔츠", 10_000L, 2L, List.of())));

    @BeforeEach
    void setUp() {
        retryService = new DeliveryRetryService(deliveryFacade, deliveryRepository);
    }

    @Test
    @DisplayName("정상 처리되면 한 번만 호출한다")
    void success() {
        retryService.createDelivery(command);

        verify(deliveryFacade, times(1)).createDelivery(command);
        verify(deliveryRepository, never()).existsByIdempotencyKey("key-1");
    }

    @Test
    @DisplayName("동시 요청으로 unique 위반이 나도 같은 키의 배송이 있으면 다시 호출해 정상 종료한다")
    void retriesWhenSameKeyExists() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .doNothing()
                .when(deliveryFacade).createDelivery(command);
        when(deliveryRepository.existsByIdempotencyKey("key-1")).thenReturn(true);

        retryService.createDelivery(command);

        verify(deliveryFacade, times(2)).createDelivery(command);
    }

    @Test
    @DisplayName("재호출에서 내용이 다르면 멱등키 충돌 예외가 전달된다")
    void conflictOnRetry() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .doThrow(new ServiceException(DeliveryErrorCode.DELIVERY_IDEMPOTENCY_CONFLICT))
                .when(deliveryFacade).createDelivery(command);
        when(deliveryRepository.existsByIdempotencyKey("key-1")).thenReturn(true);

        assertThatThrownBy(() -> retryService.createDelivery(command))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_IDEMPOTENCY_CONFLICT");
        verify(deliveryFacade, times(2)).createDelivery(command);
    }

    @Test
    @DisplayName("같은 키의 배송이 없으면 다른 제약 오류이므로 원래 예외를 그대로 던진다")
    void rethrowsWhenKeyMissing() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("other constraint");
        doThrow(original).when(deliveryFacade).createDelivery(command);
        when(deliveryRepository.existsByIdempotencyKey("key-1")).thenReturn(false);

        assertThatThrownBy(() -> retryService.createDelivery(command)).isSameAs(original);
        verify(deliveryFacade, times(1)).createDelivery(command);
    }
}
