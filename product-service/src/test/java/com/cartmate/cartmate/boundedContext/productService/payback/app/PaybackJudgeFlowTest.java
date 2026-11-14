package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.IndividualGroupPurchasePayback;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackOrderItem;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayout;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayoutStatus;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.AdminPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackPayoutResponse;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.SellerPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.out.IndividualGroupPurchasePaybackRepository;
import com.cartmate.cartmate.boundedContext.productService.payback.out.PaybackPayoutRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Category;
import com.cartmate.cartmate.boundedContext.productService.product.out.CategoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.shared.cart.GroupPurchaseType;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseOrigin;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.shared.product.event.PaybackRequestedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import com.cartmate.cartmate.support.ProductServiceJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@ProductServiceJpaTest
@Import(PaybackAppTestConfig.class)
class PaybackJudgeFlowTest {

    private static final long SELLER_ID = 70L;
    private static final long PURCHASE_ID = 1L;
    private static final long BUYER = 10L;
    private static final long OTHER_BUYER = 20L;

    @Autowired
    private PaybackFacade paybackFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private IndividualGroupPurchasePaybackRepository paybackRepository;

    @Autowired
    private PaybackPayoutRepository payoutRepository;

    @Autowired
    private PaybackAppTestConfig.RecordingEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        eventPublisher.clear();
        Category category = categoryRepository.save(new Category("유니폼 / 의류"));   // JPA 슬라이스 테스트라 data.sql 초기 데이터가 없다
        productId = productRepository.save(new Product(SELLER_ID, 1L, category, "상품", "설명", 10_000L,
                ProductStatus.ON_SALE)).getId();
    }

    @Test
    @DisplayName("종료 뒤 결제 대기 주문이 취소되고 판매자 배송이 모두 구매확정되면 한 번 판정해 구매자마다 개당 금액 × 수량을 요청한다")
    void judgesWhenAllConfirmed() {
        paybackFacade.setAdminCondition(productId, new AdminPaybackConditionRequest(3, 500L, null));
        paybackFacade.applyGroupPurchaseOpened(opened());
        place(11L, BUYER, 2, OrderStatus.PAID);
        place(12L, OTHER_BUYER, 1, OrderStatus.PAID);
        place(13L, OTHER_BUYER, 5, OrderStatus.PAYMENT_PENDING);
        paybackFacade.applyGroupPurchaseClosed(closed());

        paybackFacade.applyPurchaseConfirmed(11L, SELLER_ID);
        paybackFacade.applyPurchaseConfirmed(12L, SELLER_ID);
        assertThat(judged()).isFalse();

        paybackFacade.applyOrderCanceled(13L);
        paybackFacade.applyPurchaseConfirmed(12L, SELLER_ID);
        paybackFacade.applyGroupPurchaseClosed(closed());

        assertThat(judged()).isTrue();
        assertThat(payoutRepository.findAll())
                .extracting(PaybackPayout::getMemberId, PaybackPayout::getFunder, PaybackPayout::getQuantity,
                        PaybackPayout::getAmount, PaybackPayout::getGroupPurchaseType)
                .containsExactlyInAnyOrder(
                        tuple(BUYER, PaybackFunder.ADMIN, 2, 1_000L, GroupPurchaseType.INDIVIDUAL),
                        tuple(OTHER_BUYER, PaybackFunder.ADMIN, 1, 500L, GroupPurchaseType.INDIVIDUAL));
        assertThat(eventPublisher.events()).hasSize(2)
                .allSatisfy(event -> assertThat(event).isInstanceOfSatisfying(PaybackRequestedEvent.class,
                        requested -> assertThat(requested.groupPurchaseId()).isEqualTo(PURCHASE_ID)));
    }

    @Test
    @DisplayName("종료 · 구매확정이 개설 · 결제보다 먼저 와도 마지막 이벤트에서 같은 결과로 판정한다")
    void outOfOrder() {
        paybackFacade.setSellerCondition(SELLER_ID, productId, new SellerPaybackConditionRequest(2, 300L));
        paybackFacade.applyGroupPurchaseClosed(closed());
        paybackFacade.applyPurchaseConfirmed(21L, SELLER_ID);
        place(21L, BUYER, 3, OrderStatus.PAID);
        assertThat(judged()).isFalse();

        paybackFacade.applyGroupPurchaseOpened(opened());

        assertThat(judged()).isTrue();
        assertThat(payoutRepository.findAll()).singleElement().satisfies(payout -> {
            assertThat(payout.getFunder()).isEqualTo(PaybackFunder.SELLER);
            assertThat(payout.getSellerId()).isEqualTo(SELLER_ID);
            assertThat(payout.getAmount()).isEqualTo(900L);
        });
    }

    @Test
    @DisplayName("판매자 항목 취소된 수량은 빼고, 기준 미달이면 지급 없이 판정만 남긴다")
    void sellerCanceledAndNotReached() {
        paybackFacade.setAdminCondition(productId,
                new AdminPaybackConditionRequest(3, 500L, PaybackOverlapPolicy.BOTH));
        paybackFacade.applyGroupPurchaseOpened(opened());
        place(31L, BUYER, 2, OrderStatus.PAID);
        place(32L, OTHER_BUYER, 4, OrderStatus.PAID);
        paybackFacade.applyOrderSellerItemsCanceled(32L, SELLER_ID);
        paybackFacade.applyPurchaseConfirmed(31L, SELLER_ID);
        paybackFacade.applyGroupPurchaseClosed(closed());

        IndividualGroupPurchasePayback payback = paybackRepository.findById(PURCHASE_ID).orElseThrow();
        assertThat(payback.isJudged()).isTrue();
        assertThat(payback.isPaid()).isFalse();
        assertThat(payoutRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("조건이 없는 공동구매는 판정하지 않는다")
    void noCondition() {
        paybackFacade.applyGroupPurchaseOpened(opened());
        place(41L, BUYER, 9, OrderStatus.PAID);
        paybackFacade.applyPurchaseConfirmed(41L, SELLER_ID);
        paybackFacade.applyGroupPurchaseClosed(closed());

        assertThat(judged()).isFalse();
    }

    @Test
    @DisplayName("wallet 적립 결과로 지급 완료 · 재요청 대기가 되고, 재요청하면 시도 번호가 올라 다시 요청한다")
    void walletResults() {
        paybackFacade.setAdminCondition(productId, new AdminPaybackConditionRequest(2, 500L, null));
        paybackFacade.applyGroupPurchaseOpened(opened());
        place(51L, BUYER, 1, OrderStatus.PAID);
        place(52L, OTHER_BUYER, 1, OrderStatus.PAID);
        paybackFacade.applyPurchaseConfirmed(51L, SELLER_ID);
        paybackFacade.applyPurchaseConfirmed(52L, SELLER_ID);
        paybackFacade.applyGroupPurchaseClosed(closed());
        List<PaybackPayout> payouts = payoutRepository.findAll();
        PaybackPayout first = payouts.stream().filter(p -> p.getMemberId() == BUYER).findFirst().orElseThrow();
        PaybackPayout second = payouts.stream().filter(p -> p.getMemberId() == OTHER_BUYER).findFirst().orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        paybackFacade.applyPaybackDeposited(new PaybackDepositedEvent(first.getId(), BUYER, 500L, 1, now));
        paybackFacade.applyPaybackDepositFailed(new PaybackDepositFailedEvent(second.getId(), OTHER_BUYER,
                "WALLET_LOCKED", true, 1, now));
        eventPublisher.clear();
        paybackFacade.findRetryWaitingPaybackIds(10).forEach(paybackFacade::retryWaitingPayback);

        assertThat(payoutRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(PaybackPayoutStatus.PAID);
        PaybackPayout retried = payoutRepository.findById(second.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(PaybackPayoutStatus.REQUESTED);
        assertThat(retried.getAttempt()).isEqualTo(2);
        assertThat(eventPublisher.events()).singleElement()
                .isInstanceOfSatisfying(PaybackRequestedEvent.class,
                        event -> assertThat(event.attempt()).isEqualTo(2));
        assertThat(paybackFacade.getPaybackPayouts(PaybackPayoutStatus.PAID))
                .extracting(PaybackPayoutResponse::paybackId).containsExactly(first.getId());
    }

    @Test
    @DisplayName("마지막 두 구매확정이 동시에 와도 공동구매 행 락 + READ_COMMITTED로 판정이 빠지지 않고 한 번만 난다")
    void concurrentLastConfirmations() throws Exception {
        paybackFacade.setAdminCondition(productId, new AdminPaybackConditionRequest(2, 500L, null));
        paybackFacade.applyGroupPurchaseOpened(opened());
        place(61L, BUYER, 1, OrderStatus.PAID);
        place(62L, OTHER_BUYER, 1, OrderStatus.PAID);
        paybackFacade.applyGroupPurchaseClosed(closed());

        for (int round = 0; round < 3; round++) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            CompletableFuture<Void> first = CompletableFuture.runAsync(
                    () -> awaitThen(barrier, () -> paybackFacade.applyPurchaseConfirmed(61L, SELLER_ID)));
            CompletableFuture<Void> second = CompletableFuture.runAsync(
                    () -> awaitThen(barrier, () -> paybackFacade.applyPurchaseConfirmed(62L, SELLER_ID)));
            CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);
        }

        assertThat(judged()).isTrue();
        assertThat(payoutRepository.findAll()).hasSize(2);
    }

    private static void awaitThen(CyclicBarrier barrier, Runnable action) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        action.run();
    }

    private boolean judged() {
        return transactionTemplate.execute(status -> paybackRepository.findById(PURCHASE_ID)
                .map(IndividualGroupPurchasePayback::isJudged)
                .orElse(false));
    }

    private void place(Long orderId, Long buyer, int quantity, OrderStatus status) {
        paybackFacade.applyOrderPlaced(orderId, PURCHASE_ID, buyer,
                List.of(PaybackOrderItem.of(orderId * 10, productId, SELLER_ID, quantity)), status);
    }

    private IndividualGroupPurchaseOpenedEvent opened() {
        LocalDateTime now = LocalDateTime.now();
        return new IndividualGroupPurchaseOpenedEvent(PURCHASE_ID, now.plusDays(1), List.of(productId), 0, now);
    }

    private static IndividualGroupPurchaseClosedEvent closed() {
        LocalDateTime now = LocalDateTime.now();
        return new IndividualGroupPurchaseClosedEvent(PURCHASE_ID, IndividualGroupPurchaseOrigin.ADMIN_DEMAND,
                IndividualGroupPurchaseCloseReason.ADMIN, now.plusDays(1), now, 1);
    }
}
