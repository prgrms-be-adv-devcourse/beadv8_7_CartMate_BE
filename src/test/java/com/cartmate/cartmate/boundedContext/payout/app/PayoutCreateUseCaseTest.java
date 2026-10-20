package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutCreateUseCaseTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 19, 13, 30);
    private static final LocalDateTime ORDERED_AT = LocalDateTime.of(2026, 10, 17, 9, 0);

    private final PayoutRepository payoutRepository = mock(PayoutRepository.class);
    private PayoutCreateUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PayoutCreateUseCase(payoutRepository);
        when(payoutRepository.findAllByOrderItemIdIn(anyCollection())).thenReturn(List.of());
    }

    private DeliveryConfirmedEvent event(Item... items) {
        return new DeliveryConfirmedEvent(5L, 10L, 100L, "INDIVIDUAL_GROUP", ORDERED_AT, CONFIRMED_AT, List.of(items));
    }

    @SuppressWarnings("unchecked")
    private List<Payout> savedPayouts() {
        ArgumentCaptor<List<Payout>> captor = ArgumentCaptor.forClass(List.class);
        verify(payoutRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("상품별로 단가 x 수량 금액의 대기 정산을 만든다")
    void createPerItem() {
        useCase.create(event(new Item(1000L, "티셔츠", 12_000L, 2L), new Item(1001L, "양말", 5_000L, 1L)));

        List<Payout> payouts = savedPayouts();
        assertThat(payouts).hasSize(2);
        assertThat(payouts).extracting(Payout::getOrderItemId).containsExactly(1000L, 1001L);
        assertThat(payouts).extracting(Payout::getProductName).containsExactly("티셔츠", "양말");
        assertThat(payouts).extracting(Payout::getQuantity).containsExactly(2L, 1L);
        assertThat(payouts).extracting(Payout::getSalesAmount).containsExactly(24_000L, 5_000L);
        assertThat(payouts).extracting(Payout::getFeeAmount).containsExactly(720L, 150L);
        assertThat(payouts).extracting(Payout::getPayoutAmount).containsExactly(23_280L, 4_850L);
        assertThat(payouts).allSatisfy(payout -> {
            assertThat(payout.getSellerId()).isEqualTo(10L);
            assertThat(payout.getOrderId()).isEqualTo(100L);
            assertThat(payout.getOrderedAt()).isEqualTo(ORDERED_AT);
            assertThat(payout.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
            assertThat(payout.getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
        });
    }

    @Test
    @DisplayName("0원 상품도 정산 기록을 만든다")
    void zeroAmountItemIsRecorded() {
        useCase.create(event(new Item(1000L, "티셔츠", 0L, 1L)));

        List<Payout> payouts = savedPayouts();
        assertThat(payouts).hasSize(1);
        assertThat(payouts.get(0).getSalesAmount()).isZero();
        assertThat(payouts.get(0).getPayoutAmount()).isZero();
    }

    @Test
    @DisplayName("이미 정산이 있는 주문상품은 건너뛰고 나머지만 만든다")
    void skipExistingOrderItem() {
        Payout existing = Payout.create(10L, 100L, 1000L, "티셔츠", 2L, 24_000L, ORDERED_AT, CONFIRMED_AT);
        when(payoutRepository.findAllByOrderItemIdIn(anyCollection())).thenReturn(List.of(existing));

        useCase.create(event(new Item(1000L, "티셔츠", 12_000L, 2L), new Item(1001L, "양말", 5_000L, 1L)));

        assertThat(savedPayouts()).extracting(Payout::getOrderItemId).containsExactly(1001L);
    }

    @Test
    @DisplayName("같은 이벤트가 다시 와서 모든 상품에 정산이 있으면 새로 만들지 않는다")
    void duplicateEventCreatesNothing() {
        Payout existing = Payout.create(10L, 100L, 1000L, "티셔츠", 2L, 24_000L, ORDERED_AT, CONFIRMED_AT);
        when(payoutRepository.findAllByOrderItemIdIn(anyCollection())).thenReturn(List.of(existing));

        useCase.create(event(new Item(1000L, "티셔츠", 12_000L, 2L)));

        assertThat(savedPayouts()).isEmpty();
    }
}
