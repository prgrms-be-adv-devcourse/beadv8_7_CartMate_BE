package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliverySchedulerTest {

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryFacade deliveryFacade = mock(DeliveryFacade.class);
    private DeliveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DeliveryScheduler(deliveryRepository, deliveryFacade, 60, 7);
    }

    @Test
    @DisplayName("출발 후 설정한 시간이 지난 배송중 건을 모두 배송완료 처리한다")
    void completesShipped() {
        when(deliveryRepository.findIdsByStatusAndShippedAtBefore(any(), any())).thenReturn(List.of(1L, 2L));

        scheduler.completeShipped();

        verify(deliveryFacade).deliverDelivery(1L);
        verify(deliveryFacade).deliverDelivery(2L);
    }

    @Test
    @DisplayName("배송중 건을 지금보다 설정한 시간만큼 이전 시각을 기준으로 조회한다")
    void queriesWithCutoff() {
        when(deliveryRepository.findIdsByStatusAndShippedAtBefore(any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        scheduler.completeShipped();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deliveryRepository).findIdsByStatusAndShippedAtBefore(
                org.mockito.ArgumentMatchers.eq(DeliveryStatus.SHIPPING), cutoff.capture());
        Duration gap = Duration.between(cutoff.getValue(), before);
        assertThat(gap.getSeconds()).isBetween(59L, 61L);
        verify(deliveryFacade, never()).deliverDelivery(any());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 건은 계속 처리한다")
    void continuesAfterFailure() {
        when(deliveryRepository.findIdsByStatusAndShippedAtBefore(any(), any())).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND)).when(deliveryFacade).deliverDelivery(2L);

        scheduler.completeShipped();

        verify(deliveryFacade).deliverDelivery(1L);
        verify(deliveryFacade).deliverDelivery(2L);
        verify(deliveryFacade).deliverDelivery(3L);
    }

    @Test
    @DisplayName("배송완료 후 설정한 기간이 지난 건을 모두 구매확정 처리한다")
    void confirmsDelivered() {
        when(deliveryRepository.findIdsByStatusAndDeliveredAtBefore(any(), any())).thenReturn(List.of(1L, 2L));

        scheduler.confirmDelivered();

        verify(deliveryFacade).autoConfirmDelivery(1L);
        verify(deliveryFacade).autoConfirmDelivery(2L);
    }

    @Test
    @DisplayName("배송완료 건을 지금보다 7일 이전 시각을 기준으로 조회한다")
    void confirmQueriesWithCutoff() {
        when(deliveryRepository.findIdsByStatusAndDeliveredAtBefore(any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        scheduler.confirmDelivered();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deliveryRepository).findIdsByStatusAndDeliveredAtBefore(
                org.mockito.ArgumentMatchers.eq(DeliveryStatus.DELIVERED), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(
                before.minusDays(7), after.minusDays(7));
        verify(deliveryFacade, never()).autoConfirmDelivery(any());
    }

    @Test
    @DisplayName("자동 구매확정도 한 건이 실패해도 나머지 건은 계속 처리한다")
    void confirmContinuesAfterFailure() {
        when(deliveryRepository.findIdsByStatusAndDeliveredAtBefore(any(), any())).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS))
                .when(deliveryFacade).autoConfirmDelivery(2L);

        scheduler.confirmDelivered();

        verify(deliveryFacade).autoConfirmDelivery(1L);
        verify(deliveryFacade).autoConfirmDelivery(2L);
        verify(deliveryFacade).autoConfirmDelivery(3L);
    }

    @Test
    @DisplayName("자동 완료와 자동 구매확정은 서로의 조회 대상에 영향을 주지 않는다")
    void completeAndConfirmAreIndependent() {
        when(deliveryRepository.findIdsByStatusAndShippedAtBefore(any(), any())).thenReturn(List.of(1L));

        scheduler.completeShipped();

        verify(deliveryFacade).deliverDelivery(1L);
        verify(deliveryFacade, never()).autoConfirmDelivery(any());
        verify(deliveryRepository, never()).findIdsByStatusAndDeliveredAtBefore(any(), any());
    }
}
