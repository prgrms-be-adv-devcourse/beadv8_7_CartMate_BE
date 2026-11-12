package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliverySellerCanceledEvent;
import com.cartmate.cartmate.shared.member.SellerQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliverySellerReasonCancelUseCaseTest {

    private static final long DELIVERY_ID = 1L;
    private static final long MEMBER_ID = 70L;
    private static final long SELLER_ID = 1007L;
    private static final long ORDER_ID = 100L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final SellerQuery sellerQuery = mock(SellerQuery.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final DeliverySellerReasonCancelUseCase useCase =
            new DeliverySellerReasonCancelUseCase(deliveryRepository, sellerQuery, eventPublisher);

    private Delivery givenDelivery(PurchaseType purchaseType, boolean owner) {
        Delivery delivery = Delivery.create(7L, SELLER_ID, ORDER_ID, null, purchaseType,
                "수령인", "010", "00000", "서울", "101호", "key-1", "hash");
        when(deliveryRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(delivery));
        when(sellerQuery.isSellerOwner(MEMBER_ID, SELLER_ID)).thenReturn(owner);
        return delivery;
    }

    @Test
    @DisplayName("취소하면 주문·구매 유형·판매자·사유·취소 시각을 담아 주문 서버에 알린다")
    void cancelPublishesEvent() {
        Delivery delivery = givenDelivery(PurchaseType.SHARED_GROUP, true);

        DeliveryResponse response = useCase.cancel(DELIVERY_ID, MEMBER_ID, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        assertThat(response.status()).isEqualTo(DeliveryStatus.CANCELED);
        verify(eventPublisher).publish(new DeliverySellerCanceledEvent(
                PurchaseType.SHARED_GROUP, ORDER_ID, SELLER_ID,
                DeliveryCancelReason.SELLER_OUT_OF_STOCK, delivery.getCanceledAt()));
    }

    @Test
    @DisplayName("같은 사유로 다시 누르면 성공하지만 이벤트는 다시 내지 않는다")
    void sameReasonAgainDoesNotPublish() {
        Delivery delivery = givenDelivery(PurchaseType.INDIVIDUAL_GROUP, true);
        delivery.cancelBySeller(DeliveryCancelReason.SELLER_OUT_OF_STOCK, java.time.LocalDateTime.now());

        DeliveryResponse response = useCase.cancel(DELIVERY_ID, MEMBER_ID, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        assertThat(response.status()).isEqualTo(DeliveryStatus.CANCELED);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("다른 판매자의 배송은 취소할 수 없다")
    void otherSellerIsDenied() {
        Delivery delivery = givenDelivery(PurchaseType.INDIVIDUAL_GROUP, false);

        assertThatThrownBy(() -> useCase.cancel(DELIVERY_ID, MEMBER_ID, DeliveryCancelReason.SELLER_OTHER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_ACCESS_DENIED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("취소할 수 없으면 이벤트를 내지 않는다")
    void rejectedDoesNotPublish() {
        Delivery delivery = givenDelivery(PurchaseType.INDIVIDUAL_GROUP, true);
        delivery.cancelByBuyerRequest(java.time.LocalDateTime.now(), java.time.LocalDateTime.now());

        assertThatThrownBy(() -> useCase.cancel(DELIVERY_ID, MEMBER_ID, DeliveryCancelReason.SELLER_OTHER))
                .hasMessageContaining("DELIVERY_ALREADY_CANCELED");
        verify(eventPublisher, never()).publish(any());
    }
}
