package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCartClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.IndividualGroupPurchaseReplicaRepository;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class IndividualGroupPurchaseReplicaPaybackConditionTest {

    private static final Long PURCHASE = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

    private final IndividualGroupPurchaseReplicaRepository replicaRepository = mock(IndividualGroupPurchaseReplicaRepository.class);
    private final List<IndividualGroupPurchaseReplica> saved = new ArrayList<>();
    private final IndividualGroupPurchaseReplicaUseCase useCase = new IndividualGroupPurchaseReplicaUseCase(
            replicaRepository, mock(OrderCartClient.class), mock(EntityManager.class));

    @Test
    @DisplayName("종료가 열림보다 먼저 와도 종료 이벤트의 페이백 조건으로 닫힌 사본에 조건이 채워진다")
    void closedFirst() {
        givenStore();

        useCase.applyClosed(new IndividualGroupPurchaseClosedEvent(PURCHASE, "ADMIN_DEMAND", "ADMIN",
                NOW.plusDays(3), NOW, 2, 10, 1_000L));
        useCase.applyOpened(new IndividualGroupPurchaseOpenedEvent(PURCHASE, NOW.plusDays(3), List.of(1L), 0, NOW,
                10, 1_000L));

        IndividualGroupPurchaseReplica replica = saved.getFirst();
        assertThat(replica.isClosed()).isTrue();
        assertThat(replica.hasPaybackCondition()).isTrue();
        assertThat(replica.getPaybackMinBuyers()).isEqualTo(10);
    }

    @Test
    @DisplayName("회원 공동구매는 조건이 null이라 사본에도 조건이 없다")
    void memberPurchase() {
        givenStore();

        useCase.applyOpened(new IndividualGroupPurchaseOpenedEvent(PURCHASE, NOW.plusDays(2), List.of(1L), 0, NOW,
                null, null));

        assertThat(saved.getFirst().hasPaybackCondition()).isFalse();
    }

    private void givenStore() {
        given(replicaRepository.findByIdForUpdate(anyLong())).willAnswer(invocation -> saved.stream().findFirst());
        given(replicaRepository.save(any())).willAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }
}
