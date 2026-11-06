package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 다른 스케줄러는 끈 채 아웃박스 릴레이 빈만 넣고, 자동 폴링은 1시간 뒤로 미뤄 테스트가 직접 돌린다
@CartMateIntegrationTest
class OrderFlowIntegrationTest {

    private static final DeliveryAddressRequest ADDRESS =
            new DeliveryAddressRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    private final long base = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @Test
    @DisplayName("장바구니 상품으로 주문하고 결제하면 주문은 결제 완료가 되고 주문한 상품만 장바구니에서 빠진다")
    void personalOrderFlow() {
        long buyer = nextMember();
        cartFacade.addItem(buyer, 1L, 2);
        cartFacade.addItem(buyer, 2L, 1);
        cartFacade.addItem(buyer, 4L, 1);
        List<Long> orderedCartItemIds = cartFacade.getCart(buyer).items().stream()
                .filter(item -> item.inventoryId() != 2L)
                .map(CartItemResponse::cartItemId)
                .toList();

        OrderCreateResponse created = orderFacade.createPersonal(buyer, new OrderCreateRequest(orderedCartItemIds, ADDRESS));
        assertThat(created.totalAmount()).isEqualTo(103000L);

        orderFacade.applyPayment(created.orderId(), buyer, created.orderId(), LocalDateTime.now());

        assertThat(orderFacade.getDetail(buyer, created.orderId()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(cartFacade.getCart(buyer).items()).extracting(CartItemResponse::inventoryId).containsExactly(2L);
    }

    @Test
    @DisplayName("공동구매 상품 주문이 결제 대기면 상품을 뺄 수 없고, 결제하면 현황판에 집계된다")
    void individualGroupOrderFlow() {
        long creator = nextMember();
        long buyer = nextMember();
        Long purchaseId = individualGroupPurchaseFacade.create(creator,
                new IndividualGroupPurchaseCreateRequest("개인물품 통합", List.of(1L, 2L))).id();

        OrderCreateResponse created = orderFacade.createIndividualGroup(buyer, new IndividualGroupOrderCreateRequest(
                purchaseId, List.of(new IndividualGroupOrderCreateRequest.Item(4L, 2)), ADDRESS));

        assertThatThrownBy(() -> individualGroupPurchaseFacade.removeProduct(creator, purchaseId, 2L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_PRODUCT_HAS_ORDERS.getMsg());
        assertThat(individualGroupPurchaseFacade.getDashboard(creator, purchaseId).orderCount()).isZero();

        orderFacade.applyPayment(created.orderId(), buyer, created.orderId(), LocalDateTime.now());
        // 현황판은 order 이벤트로 채우는 cart 읽기 모델이다. 결제 직후엔 아직 비어 있고, 릴레이가 돌면 나온다
        assertThat(individualGroupPurchaseFacade.getDashboard(creator, purchaseId).orderCount()).isZero();
        relayUntilPublished(created.orderId());

        IndividualGroupPurchaseDashboardResponse dashboard = individualGroupPurchaseFacade.getDashboard(creator, purchaseId);
        assertThat(dashboard.orderCount()).isEqualTo(1);
        assertThat(dashboard.buyerCount()).isEqualTo(1);
        assertThat(dashboard.totalQuantity()).isEqualTo(2);
        assertThat(dashboard.totalAmount()).isEqualTo(50000L);
    }

    // 이 주문의 이벤트가 모두 넘어갈 때까지 릴레이를 돌린다
    private void relayUntilPublished(Long orderId) {
        for (int i = 0; i < 20; i++) {
            ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
            outboxRelay.relay();
            Integer pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'Order' AND aggregate_id = ? AND status = 'NEW'",
                    Integer.class, String.valueOf(orderId));
            if (pending == 0) {
                return;
            }
        }
        throw new AssertionError("아웃박스 이벤트가 릴레이되지 않았다: orderId=" + orderId);
    }

    private long nextMember() {
        long memberId = base + sequence++;
        return memberId;
    }
}
