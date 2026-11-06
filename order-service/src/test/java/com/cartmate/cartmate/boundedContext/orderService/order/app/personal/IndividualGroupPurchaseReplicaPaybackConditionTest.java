package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.ReplicaPaybackCondition;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;

import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseOrigin;

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
    private static final PaybackConditionPayload ADMIN_CONDITION =
            new PaybackConditionPayload(1L, PaybackFunder.ADMIN, null, 10, 1_000L);

    private final IndividualGroupPurchaseReplicaRepository replicaRepository = mock(IndividualGroupPurchaseReplicaRepository.class);
    private final List<IndividualGroupPurchaseReplica> saved = new ArrayList<>();
    private final IndividualGroupPurchaseReplicaUseCase useCase = new IndividualGroupPurchaseReplicaUseCase(
            replicaRepository, mock(OrderCartClient.class), mock(EntityManager.class));

    @Test
    @DisplayName("종료가 열림보다 먼저 와도 종료 이벤트의 페이백 조건으로 닫힌 사본에 조건이 채워진다")
    void closedFirst() {
        givenStore();

        useCase.applyClosed(new IndividualGroupPurchaseClosedEvent(PURCHASE, IndividualGroupPurchaseOrigin.ADMIN_DEMAND, IndividualGroupPurchaseCloseReason.ADMIN,
                NOW.plusDays(3), NOW, 2, PaybackOverlapPolicy.LARGER, List.of(ADMIN_CONDITION)));
        useCase.applyOpened(new IndividualGroupPurchaseOpenedEvent(PURCHASE, NOW.plusDays(3), List.of(1L), 0, NOW,
                PaybackOverlapPolicy.BOTH, List.of(new PaybackConditionPayload(1L, PaybackFunder.ADMIN, null, 20, 900L))));

        IndividualGroupPurchaseReplica replica = saved.getFirst();
        assertThat(replica.isClosed()).isTrue();
        assertThat(replica.hasPaybackCondition()).isTrue();
        assertThat(replica.getPaybackOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.LARGER);
        assertThat(replica.getPaybackConditions()).singleElement()
                .extracting(ReplicaPaybackCondition::getMinQuantity, ReplicaPaybackCondition::getAmountPerUnit)
                .containsExactly(10, 1_000L);
    }

    @Test
    @DisplayName("변경 이벤트로 온 판매자 조건도 사본에 더해지고, 이미 있는 (상품, 부담 주체) 조건은 바뀌지 않는다")
    void updatedAddsSellerCondition() {
        givenStore();
        useCase.applyOpened(new IndividualGroupPurchaseOpenedEvent(PURCHASE, NOW.plusDays(2), List.of(1L), 0, NOW,
                PaybackOverlapPolicy.BOTH, List.of(ADMIN_CONDITION)));

        PaybackConditionPayload seller = new PaybackConditionPayload(2L, PaybackFunder.SELLER, 77L, 100, 300L);
        useCase.applyUpdated(new IndividualGroupPurchaseUpdatedEvent(PURCHASE, NOW.plusDays(2), List.of(1L, 2L), 1,
                NOW, PaybackOverlapPolicy.BOTH, List.of(
                        new PaybackConditionPayload(1L, PaybackFunder.ADMIN, null, 99, 9L), seller)));

        IndividualGroupPurchaseReplica replica = saved.getFirst();
        assertThat(replica.getPaybackConditions()).hasSize(2);
        assertThat(replica.getPaybackConditions())
                .filteredOn(ReplicaPaybackCondition::isAdmin).singleElement()
                .extracting(ReplicaPaybackCondition::getMinQuantity).isEqualTo(10);
        assertThat(replica.getPaybackConditions())
                .anySatisfy(condition -> {
                    assertThat(condition.getFunder()).isEqualTo(PaybackFunder.SELLER);
                    assertThat(condition.getSellerId()).isEqualTo(77L);
                    assertThat(condition.getAmountPerUnit()).isEqualTo(300L);
                });
    }

    @Test
    @DisplayName("조건 없는 회원 공동구매는 사본에도 조건이 없다")
    void memberPurchase() {
        givenStore();

        useCase.applyOpened(new IndividualGroupPurchaseOpenedEvent(PURCHASE, NOW.plusDays(2), List.of(1L), 0, NOW,
                PaybackOverlapPolicy.BOTH, List.of()));

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
