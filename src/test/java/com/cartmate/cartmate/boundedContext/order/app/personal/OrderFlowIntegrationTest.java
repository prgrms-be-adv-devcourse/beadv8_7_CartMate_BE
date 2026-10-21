package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
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
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private IndividualGroupPurchaseRepository individualGroupPurchaseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> purchaseIds = new ArrayList<>();

    private final long base = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void cleanUp() {
        new TestDataCleaner(jdbcTemplate).deleteDeliveriesOf(memberIds);
        orderIds.forEach(orderRepository::deleteById);
        memberIds.forEach(memberId -> cartRepository.findByMemberId(memberId).ifPresent(cartRepository::delete));
        purchaseIds.forEach(individualGroupPurchaseRepository::deleteById);
    }

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
        orderIds.add(created.orderId());
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
        purchaseIds.add(purchaseId);

        OrderCreateResponse created = orderFacade.createIndividualGroup(buyer, new IndividualGroupOrderCreateRequest(
                purchaseId, List.of(new IndividualGroupOrderCreateRequest.Item(4L, 2)), ADDRESS));
        orderIds.add(created.orderId());

        assertThatThrownBy(() -> individualGroupPurchaseFacade.removeProduct(creator, purchaseId, 2L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_PRODUCT_HAS_ORDERS.getMsg());
        assertThat(individualGroupPurchaseFacade.getDashboard(creator, purchaseId).orderCount()).isZero();

        orderFacade.applyPayment(created.orderId(), buyer, created.orderId(), LocalDateTime.now());

        IndividualGroupPurchaseDashboardResponse dashboard = individualGroupPurchaseFacade.getDashboard(creator, purchaseId);
        assertThat(dashboard.orderCount()).isEqualTo(1);
        assertThat(dashboard.buyerCount()).isEqualTo(1);
        assertThat(dashboard.totalQuantity()).isEqualTo(2);
        assertThat(dashboard.totalAmount()).isEqualTo(50000L);
    }

    private long nextMember() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
