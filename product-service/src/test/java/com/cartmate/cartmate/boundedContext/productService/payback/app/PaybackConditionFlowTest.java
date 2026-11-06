package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.IndividualGroupPurchasePayback;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackConditionSnapshot;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.AdminPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackConditionResponse;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.SellerPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.out.IndividualGroupPurchasePaybackRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseOrigin;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.product.ProductStatus;
import com.cartmate.cartmate.support.ProductServiceJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ProductServiceJpaTest
@Import({PaybackFacade.class, PaybackConditionUseCase.class, GroupPurchaseSnapshotUseCase.class})
class PaybackConditionFlowTest {

    private static final long SELLER_ID = 70L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Autowired
    private PaybackFacade paybackFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private IndividualGroupPurchasePaybackRepository paybackRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productId;
    private Long otherProductId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(product(SELLER_ID)).getId();
        otherProductId = productRepository.save(product(SELLER_ID)).getId();
    }

    @Test
    @DisplayName("어드민 · 판매자 조건을 걸면 공개 조회에 둘 다 보이고, 다시 걸면 값이 바뀐다")
    void setAndFindConditions() {
        paybackFacade.setAdminCondition(productId, new AdminPaybackConditionRequest(10, 500L, PaybackOverlapPolicy.LARGER));
        paybackFacade.setSellerCondition(SELLER_ID, productId, new SellerPaybackConditionRequest(5, 300L));
        paybackFacade.setSellerCondition(SELLER_ID, productId, new SellerPaybackConditionRequest(6, 400L));

        List<PaybackConditionResponse> conditions = paybackFacade.getConditions(productId);

        assertThat(conditions).extracting(PaybackConditionResponse::funder, PaybackConditionResponse::minQuantity,
                        PaybackConditionResponse::amountPerUnit, PaybackConditionResponse::overlapPolicy)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PaybackFunder.ADMIN, 10, 500L, PaybackOverlapPolicy.LARGER),
                        org.assertj.core.groups.Tuple.tuple(PaybackFunder.SELLER, 6, 400L, null));
    }

    @Test
    @DisplayName("판매자가 아니거나 다른 판매자 상품이면 조건을 걸 수 없다")
    void sellerMustOwnProduct() {
        assertThatThrownBy(() -> paybackFacade.setSellerCondition(null, productId,
                new SellerPaybackConditionRequest(5, 300L)))
                .isInstanceOf(ServiceException.class)
                .extracting("resultCode").isEqualTo("403-PAYBACK_SELLER_ONLY");
        assertThatThrownBy(() -> paybackFacade.setSellerCondition(SELLER_ID + 1, productId,
                new SellerPaybackConditionRequest(5, 300L)))
                .isInstanceOf(ServiceException.class)
                .extracting("resultCode").isEqualTo("403-PAYBACK_NOT_PRODUCT_OWNER");
        assertThat(paybackFacade.getConditions(productId)).isEmpty();
    }

    @Test
    @DisplayName("없는 상품이나 없는 조건은 404로 거절한다")
    void notFound() {
        assertThatThrownBy(() -> paybackFacade.setAdminCondition(999_999L,
                new AdminPaybackConditionRequest(10, 500L, null)))
                .isInstanceOf(ServiceException.class)
                .extracting("resultCode").isEqualTo("404-PAYBACK_PRODUCT_NOT_FOUND");
        assertThatThrownBy(() -> paybackFacade.releaseAdminCondition(productId))
                .isInstanceOf(ServiceException.class)
                .extracting("resultCode").isEqualTo("404-PAYBACK_CONDITION_NOT_FOUND");
    }

    @Test
    @DisplayName("상품이 공동구매에 들어갈 때 조건을 스냅샷하고, 이후 조건을 바꾸거나 해제해도 스냅샷은 그대로다")
    void snapshotOnEnter() {
        paybackFacade.setAdminCondition(productId, new AdminPaybackConditionRequest(10, 500L, PaybackOverlapPolicy.LARGER));
        paybackFacade.applyGroupPurchaseOpened(opened(1L, List.of(productId), 0));

        paybackFacade.setAdminCondition(productId, new AdminPaybackConditionRequest(20, 900L, null));
        paybackFacade.setSellerCondition(SELLER_ID, productId, new SellerPaybackConditionRequest(5, 300L));
        paybackFacade.applyGroupPurchaseUpdated(updated(1L, List.of(productId), 1));
        paybackFacade.releaseAdminCondition(productId);

        assertThat(snapshots(1L)).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getFunder()).isEqualTo(PaybackFunder.ADMIN);
            assertThat(snapshot.getMinQuantity()).isEqualTo(10);
            assertThat(snapshot.getAmountPerUnit()).isEqualTo(500L);
            assertThat(snapshot.getOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.LARGER);
        });
    }

    @Test
    @DisplayName("나중에 추가된 상품은 추가된 때의 조건을 스냅샷하고, 다른 공동구매는 자기 스냅샷을 갖는다")
    void snapshotOnProductAdded() {
        paybackFacade.applyGroupPurchaseOpened(opened(1L, List.of(productId), 0));
        paybackFacade.setSellerCondition(SELLER_ID, otherProductId, new SellerPaybackConditionRequest(5, 300L));
        paybackFacade.applyGroupPurchaseUpdated(updated(1L, List.of(productId, otherProductId), 1));
        paybackFacade.applyGroupPurchaseOpened(opened(2L, List.of(otherProductId), 0));

        assertThat(snapshots(1L)).extracting(PaybackConditionSnapshot::getProductId,
                        PaybackConditionSnapshot::getSellerId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(otherProductId, SELLER_ID));
        assertThat(snapshots(2L)).hasSize(1);
    }

    @Test
    @DisplayName("종료가 먼저 와도 공동구매 행이 생기고, 같은 이벤트가 다시 와도 결과가 같다")
    void closedFirstAndIdempotent() {
        paybackFacade.applyGroupPurchaseClosed(closed(1L, 3));
        paybackFacade.applyGroupPurchaseOpened(opened(1L, List.of(productId), 0));
        paybackFacade.applyGroupPurchaseOpened(opened(1L, List.of(productId), 0));
        paybackFacade.applyGroupPurchaseClosed(closed(1L, 3));

        IndividualGroupPurchasePayback payback = paybackRepository.findById(1L).orElseThrow();
        assertThat(payback.isOpened()).isTrue();
        assertThat(payback.isClosed()).isTrue();
        assertThat(payback.getRevision()).isEqualTo(3);
        assertThat(payback.isEnded(NOW)).isTrue();
    }

    private List<PaybackConditionSnapshot> snapshots(Long purchaseId) {
        return transactionTemplate.execute(status ->
                List.copyOf(paybackRepository.findById(purchaseId).orElseThrow().getConditionSnapshots()));
    }

    private static Product product(Long sellerId) {
        return new Product(sellerId, 1L, 1L, "상품", "설명", 10_000L, ProductStatus.ON_SALE, true);
    }

    private static IndividualGroupPurchaseOpenedEvent opened(Long purchaseId, List<Long> productIds, long revision) {
        return new IndividualGroupPurchaseOpenedEvent(purchaseId, NOW.plusDays(1), productIds, revision, NOW,
                null, null);
    }

    private static IndividualGroupPurchaseUpdatedEvent updated(Long purchaseId, List<Long> productIds, long revision) {
        return new IndividualGroupPurchaseUpdatedEvent(purchaseId, NOW.plusDays(1), productIds, revision, NOW,
                null, null);
    }

    private static IndividualGroupPurchaseClosedEvent closed(Long purchaseId, long revision) {
        return new IndividualGroupPurchaseClosedEvent(purchaseId, IndividualGroupPurchaseOrigin.ADMIN_DEMAND,
                IndividualGroupPurchaseCloseReason.ADMIN, NOW.minusHours(1), NOW.minusHours(1), revision,
                null, null);
    }
}
