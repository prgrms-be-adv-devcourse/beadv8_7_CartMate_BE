package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplica;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.PaybackJudgment;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.PaybackOrderOutcomeType;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseOrderReplicaRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.PaybackJudgmentRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.PaybackOrderOutcomeRepository;
import com.cartmate.cartmate.global.global.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PaybackJudgeUseCase.class, QuerydslConfig.class})
class PaybackJudgeUseCaseTest {

    private static final Long ADMIN = 9_300_000_001L;
    private static final Long PRODUCT = 9_300_000_101L;
    private static final Long BUYER_A = 9_300_000_201L;
    private static final Long BUYER_B = 9_300_000_202L;
    private static final Long ORDER_1 = 9_300_000_301L;
    private static final Long ORDER_2 = 9_300_000_302L;
    private static final Long ORDER_3 = 9_300_000_303L;

    @Autowired
    private PaybackJudgeUseCase useCase;

    @Autowired
    private IndividualGroupPurchaseRepository purchaseRepository;

    @Autowired
    private IndividualGroupPurchaseOrderReplicaRepository replicaRepository;

    @Autowired
    private PaybackOrderOutcomeRepository outcomeRepository;

    @Autowired
    private PaybackJudgmentRepository judgmentRepository;

    @Autowired
    private EntityManager em;

    private final LocalDateTime openedAt = LocalDateTime.of(2026, 11, 5, 12, 0);
    private final LocalDateTime later = openedAt.plusDays(10);

    @Test
    @DisplayName("끝난 공동구매의 결제 완료 주문이 모두 구매확정되면 서로 다른 구매자 수로 한 번 판정한다")
    void judgeWhenAllConfirmed() {
        Long purchaseId = closedDemandPurchase(2);
        paidOrder(ORDER_1, purchaseId, BUYER_A);
        paidOrder(ORDER_2, purchaseId, BUYER_A);
        paidOrder(ORDER_3, purchaseId, BUYER_B);

        useCase.recordConfirmed(ORDER_1, later, later);
        useCase.recordConfirmed(ORDER_2, later, later);
        assertThat(judgmentRepository.findById(purchaseId)).isEmpty();

        useCase.recordConfirmed(ORDER_3, later, later);

        PaybackJudgment judgment = judgmentRepository.findById(purchaseId).orElseThrow();
        assertThat(judgment.getBuyerCount()).isEqualTo(2);
        assertThat(judgment.isReached()).isTrue();
        assertThat(judgment.getPaybackAmount()).isEqualTo(1_000L);
        assertThat(judgment.getJudgedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("마지막으로 남은 주문이 취소되면 그때 판정하고, 취소한 구매자는 세지 않는다")
    void judgeWhenLastCanceled() {
        Long purchaseId = closedDemandPurchase(2);
        paidOrder(ORDER_1, purchaseId, BUYER_A);
        paidOrder(ORDER_2, purchaseId, BUYER_B);

        useCase.recordConfirmed(ORDER_1, later, later);
        useCase.recordCanceled(ORDER_2, later, later);

        PaybackJudgment judgment = judgmentRepository.findById(purchaseId).orElseThrow();
        assertThat(judgment.getBuyerCount()).isEqualTo(1);
        assertThat(judgment.isReached()).isFalse();
    }

    @Test
    @DisplayName("아직 판매 중이면 모두 구매확정이어도 판정하지 않고, 끝난 뒤 종료 이벤트로 판정한다")
    void notJudgedWhileOpen() {
        IndividualGroupPurchase purchase = purchaseRepository.save(IndividualGroupPurchase.openFromDemand(
                "수요 공동구매", ADMIN, "mat10open001", PRODUCT, openedAt.plusDays(7), 2, 1_000L, openedAt));
        paidOrder(ORDER_1, purchase.getId(), BUYER_A);

        useCase.recordConfirmed(ORDER_1, openedAt.plusDays(3), openedAt.plusDays(3));
        assertThat(judgmentRepository.findById(purchase.getId())).isEmpty();

        useCase.judge(purchase.getId(), openedAt.plusDays(8));

        assertThat(judgmentRepository.findById(purchase.getId()))
                .hasValueSatisfying(judgment -> assertThat(judgment.getBuyerCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("같은 구매확정이 두 번 와도 기록 1건 · 판정 1건이고, 이미 판정했으면 다시 판정하지 않는다")
    void idempotent() {
        Long purchaseId = closedDemandPurchase(2);
        paidOrder(ORDER_1, purchaseId, BUYER_A);

        useCase.recordConfirmed(ORDER_1, later, later);
        useCase.recordConfirmed(ORDER_1, later.plusDays(1), later.plusDays(1));
        useCase.judge(purchaseId, later.plusDays(2));
        flushAndClear();

        assertThat(outcomeRepository.findById(ORDER_1)).hasValueSatisfying(
                outcome -> assertThat(outcome.getOccurredAt()).isEqualTo(later));
        assertThat(judgmentRepository.findById(purchaseId)).hasValueSatisfying(
                judgment -> assertThat(judgment.getJudgedAt()).isEqualTo(later));
    }

    @Test
    @DisplayName("주문 없이 끝난 공동구매는 종료 이벤트로 0명 미달 판정한다")
    void noOrders() {
        Long purchaseId = closedDemandPurchase(2);

        useCase.judge(purchaseId, later);

        assertThat(judgmentRepository.findById(purchaseId)).hasValueSatisfying(judgment -> {
            assertThat(judgment.getBuyerCount()).isZero();
            assertThat(judgment.isReached()).isFalse();
        });
    }

    @Test
    @DisplayName("회원이 만든 공동구매 주문의 구매확정은 기록하지도 판정하지도 않는다")
    void ignoreMemberPurchase() {
        IndividualGroupPurchase purchase = purchaseRepository.save(IndividualGroupPurchase.create(
                "회원 공동구매", ADMIN, "mat10member1", List.of(PRODUCT), openedAt));
        paidOrder(ORDER_1, purchase.getId(), BUYER_A);

        useCase.recordConfirmed(ORDER_1, later, later);
        useCase.judge(purchase.getId(), later);

        assertThat(outcomeRepository.findById(ORDER_1)).isEmpty();
        assertThat(judgmentRepository.findById(purchase.getId())).isEmpty();
    }

    @Test
    @DisplayName("읽기 모델에 아직 없는 주문의 구매확정도 기록해 두고, 나중 판정 때 반영한다")
    void confirmedBeforeReplica() {
        Long purchaseId = closedDemandPurchase(2);
        useCase.recordConfirmed(ORDER_1, later, later);
        assertThat(outcomeRepository.findById(ORDER_1)).isPresent();

        paidOrder(ORDER_1, purchaseId, BUYER_A);
        useCase.judge(purchaseId, later);

        assertThat(judgmentRepository.findById(purchaseId))
                .hasValueSatisfying(judgment -> assertThat(judgment.getBuyerCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("결제 대기 주문이 남아 있으면 판정하지 않고, 그 주문이 취소되면 판정한다")
    void pendingBlocksUntilCanceled() {
        Long purchaseId = closedDemandPurchase(2);
        paidOrder(ORDER_1, purchaseId, BUYER_A);
        pendingOrder(ORDER_2, purchaseId, BUYER_B);

        useCase.recordConfirmed(ORDER_1, later, later);
        assertThat(judgmentRepository.findById(purchaseId)).isEmpty();

        useCase.recordCanceled(ORDER_2, later, later);

        assertThat(judgmentRepository.findById(purchaseId))
                .hasValueSatisfying(judgment -> assertThat(judgment.getBuyerCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("판매자 항목 취소는 그 판매자 항목이 주문 전부일 때만 주문 취소로 기록한다")
    void sellerItemsCanceled() {
        Long purchaseId = closedDemandPurchase(2);
        paidOrder(ORDER_1, purchaseId, BUYER_A);

        useCase.recordSellerItemsCanceled(ORDER_1, 2L, later, later);
        assertThat(outcomeRepository.findById(ORDER_1)).isEmpty();

        useCase.recordSellerItemsCanceled(ORDER_1, 1L, later, later);
        assertThat(outcomeRepository.findById(ORDER_1)).hasValueSatisfying(
                outcome -> assertThat(outcome.getOutcome()).isEqualTo(PaybackOrderOutcomeType.CANCELED));
    }

    private Long closedDemandPurchase(int minBuyers) {
        IndividualGroupPurchase purchase = IndividualGroupPurchase.openFromDemand(
                "수요 공동구매", ADMIN, "mat10" + System.nanoTime() % 10_000_000L, PRODUCT, null, minBuyers, 1_000L, openedAt);
        purchase.closeByAdmin(openedAt.plusDays(1));
        return purchaseRepository.save(purchase).getId();
    }

    private void pendingOrder(Long orderId, Long purchaseId, Long buyer) {
        IndividualGroupPurchaseOrderReplica replica = IndividualGroupPurchaseOrderReplica.create(orderId);
        replica.applyCreated(purchaseId, buyer, openedAt.plusHours(1), List.of(
                new IndividualGroupPurchaseOrderReplica.Line(orderId * 10, PRODUCT, PRODUCT, 1L, 1, 10_000L)));
        replicaRepository.save(replica);
        flushAndClear();
    }

    private void paidOrder(Long orderId, Long purchaseId, Long buyer) {
        IndividualGroupPurchaseOrderReplica replica = IndividualGroupPurchaseOrderReplica.create(orderId);
        replica.applyPaid(purchaseId, buyer, openedAt.plusHours(1), List.of(
                new IndividualGroupPurchaseOrderReplica.Line(orderId * 10, PRODUCT, PRODUCT, 1L, 1, 10_000L)));
        replicaRepository.save(replica);
        flushAndClear();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
