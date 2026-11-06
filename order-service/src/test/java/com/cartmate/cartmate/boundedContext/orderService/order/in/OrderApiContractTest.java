package com.cartmate.cartmate.boundedContext.orderService.order.in;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackSettlement;
import com.cartmate.cartmate.shared.order.GroupPurchaseType;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayout;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.PaybackPayoutRepository;
import com.cartmate.cartmate.support.ExternalApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.sharedPurchase;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderApiContractTest extends ExternalApiTestSupport {

    private static final long BUYER = 630_000_001L;
    private static final long OTHER_BUYER = 630_000_002L;
    private static final long CREATOR = 630_000_011L;
    private static final long MEMBER = 630_000_012L;
    private static final long ADMIN = 630_000_100L;
    private static final String ADDRESS_JSON = "{\"receiverName\":\"홍길동\",\"receiverPhone\":\"010-1234-5678\","
            + "\"zipCode\":\"12345\",\"address\":\"서울시 강남구\",\"addressDetail\":\"101호\"}";
    private static final DeliveryAddressRequest ADDRESS =
            new DeliveryAddressRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @Autowired
    private SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private PaybackPayoutRepository paybackPayoutRepository;

    @Test
    @DisplayName("장바구니 상품으로 주문하면 201과 주문 id · 번호 · 금액 · 결제 기한을 주고, 목록 · 상세를 계약된 필드로 준다")
    void personalOrder() throws Exception {
        cartFacade.addItem(BUYER, 1L, 2);
        Long cartItemId = cartFacade.getCart(BUYER).items().get(0).cartItemId();

        created(json(post("/api/orders"), "{\"cartItemIds\":[" + cartItemId + "],\"deliveryAddress\":" + ADDRESS_JSON + "}")
                .with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.orderId").isNumber())
                .andExpect(jsonPath("$.data.orderNumber").isString())
                .andExpect(jsonPath("$.data.totalAmount", equalTo(78_000)))
                .andExpect(jsonPath("$.data.paymentDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
        Long orderId = orderFacade.getMyList(BUYER).get(0).id();

        ok(get("/api/orders/me").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", equalTo(num(orderId))))
                .andExpect(jsonPath("$.data[0].orderNumber").isString())
                .andExpect(jsonPath("$.data[0].orderType").value("PERSONAL"))
                .andExpect(jsonPath("$.data[0].status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data[0].totalAmount", equalTo(78_000)))
                .andExpect(jsonPath("$.data[0].itemCount", equalTo(1)))
                .andExpect(jsonPath("$.data[0].firstProductName").value("축구 유니폼"))
                .andExpect(jsonPath("$.data[0].paymentDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data[0].createdAt", matchesPattern(ISO_LOCAL_DATE_TIME)));

        ok(get("/api/orders/{id}", orderId).with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.id", equalTo(num(orderId))))
                .andExpect(jsonPath("$.data.orderNumber").isString())
                .andExpect(jsonPath("$.data.orderType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.individualGroupPurchaseId").value(nullValue()))
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.cancelReason").value(nullValue()))
                .andExpect(jsonPath("$.data.totalAmount", equalTo(78_000)))
                .andExpect(jsonPath("$.data.paymentDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.paidAt").value(nullValue()))
                .andExpect(jsonPath("$.data.canceledAt").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.deliveryAddress.receiverName").value("홍길동"))
                .andExpect(jsonPath("$.data.deliveryAddress.receiverPhone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.deliveryAddress.zipCode").value("12345"))
                .andExpect(jsonPath("$.data.deliveryAddress.address").value("서울시 강남구"))
                .andExpect(jsonPath("$.data.deliveryAddress.addressDetail").value("101호"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].itemId").isNumber())
                .andExpect(jsonPath("$.data.items[0].productId", equalTo(1)))
                .andExpect(jsonPath("$.data.items[0].inventoryId", equalTo(1)))
                .andExpect(jsonPath("$.data.items[0].productName").value("축구 유니폼"))
                .andExpect(jsonPath("$.data.items[0].optionName").value("M / 레드"))
                .andExpect(jsonPath("$.data.items[0].unitPrice", equalTo(39_000)))
                .andExpect(jsonPath("$.data.items[0].quantity", equalTo(2)))
                .andExpect(jsonPath("$.data.items[0].amount", equalTo(78_000)));
    }

    @Test
    @DisplayName("결제 미리보기와 결제는 경로의 주문 id로 동작하고 금액 · 잔액 · 결제 결과를 계약된 필드로 준다")
    void personalPayment() throws Exception {
        Long orderId = createPersonalOrder(BUYER);

        ok(get("/api/orders/{orderId}/payment-preview", orderId).with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.orderId", equalTo(num(orderId))))
                .andExpect(jsonPath("$.data.amount", equalTo(78_000)))
                .andExpect(jsonPath("$.data.payable").value(true))
                .andExpect(jsonPath("$.data.walletBalance", equalTo(1_000_000_000)))
                .andExpect(jsonPath("$.data.balanceAfter", equalTo(999_922_000)))
                .andExpect(jsonPath("$.data.sufficient").value(true))
                .andExpect(jsonPath("$.data.existingPayment").value(nullValue()));

        ok(post("/api/orders/{orderId}/payment", orderId).with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.paymentId").isNumber())
                .andExpect(jsonPath("$.data.orderId", equalTo(num(orderId))))
                .andExpect(jsonPath("$.data.amount", equalTo(78_000)))
                .andExpect(jsonPath("$.data.refundedAmount", equalTo(0)))
                .andExpect(jsonPath("$.data.refundableAmount", equalTo(78_000)))
                .andExpect(jsonPath("$.data.type").value("INDIVIDUAL_GROUP"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.canceledAt").value(nullValue()));
        ok(get("/api/orders/{id}", orderId).with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("주문 취소는 data 없이 200을 주고, 남의 주문은 토큰의 회원 기준으로 막혀 403과 업무 오류 resultCode를 준다")
    void cancelAndOthersOrder() throws Exception {
        Long orderId = createPersonalOrder(BUYER);

        mvc.perform(get("/api/orders/{id}", orderId).with(buyer(OTHER_BUYER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-ORDER_BUYER_ONLY"))
                .andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(post("/api/orders/{id}/cancel", orderId).with(buyer(OTHER_BUYER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-ORDER_BUYER_ONLY"));

        ok(post("/api/orders/{id}/cancel", orderId).with(buyer(BUYER)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        ok(get("/api/orders/{id}", orderId).with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
    }

    @Test
    @DisplayName("개인물품 공동구매 주문은 공동구매 id · 상품 · 배송지를 본문으로 받아 201과 주문 생성 응답을 준다")
    void individualGroupOrder() throws Exception {
        Long purchaseId = individualGroupPurchaseFacade.create(CREATOR,
                new IndividualGroupPurchaseCreateRequest("OO FC 유니폼", List.of(2L))).id();

        created(json(post("/api/orders/individual-group"), "{\"individualGroupPurchaseId\":" + purchaseId
                + ",\"items\":[{\"inventoryId\":4,\"quantity\":2}],\"deliveryAddress\":" + ADDRESS_JSON + "}")
                .with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.orderId").isNumber())
                .andExpect(jsonPath("$.data.totalAmount", equalTo(50_000)))
                .andExpect(jsonPath("$.data.paymentDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
        ok(get("/api/orders/me").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data[0].orderType").value("INDIVIDUAL_GROUP"));
    }

    @Test
    @DisplayName("공동 주문 목록과 공동구매 id로 찾는 상세는 조회한 회원 기준의 분담금 · 결제 상태를 계약된 필드로 준다")
    void sharedOrderRead() throws Exception {
        SharedOrder order = saveSharedOrder();

        ok(get("/api/group-orders/shared/me").with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", equalTo(num(order.id()))))
                .andExpect(jsonPath("$.data[0].sharedGroupPurchaseId", equalTo(num(order.purchaseId()))))
                .andExpect(jsonPath("$.data[0].status").value("PAYING"))
                .andExpect(jsonPath("$.data[0].creator").value(false))
                .andExpect(jsonPath("$.data[0].totalAmount", equalTo(50_001)))
                .andExpect(jsonPath("$.data[0].myShareAmount", equalTo(25_000)))
                .andExpect(jsonPath("$.data[0].myPaymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data[0].paidCount", equalTo(0)))
                .andExpect(jsonPath("$.data[0].participantCount", equalTo(2)))
                .andExpect(jsonPath("$.data[0].paymentDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data[0].createdAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data[0].purchaseTitle").value("FC 훈련용품"))
                .andExpect(jsonPath("$.data[0].firstProductName").value("상품"))
                .andExpect(jsonPath("$.data[0].itemCount", equalTo(1)));

        ok(get("/api/group-orders/shared").param("purchaseId", String.valueOf(order.purchaseId())).with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data.id", equalTo(num(order.id()))))
                .andExpect(jsonPath("$.data.sharedGroupPurchaseId", equalTo(num(order.purchaseId()))))
                .andExpect(jsonPath("$.data.status").value("PAYING"))
                .andExpect(jsonPath("$.data.cancelReason").value(nullValue()))
                .andExpect(jsonPath("$.data.creator").value(false))
                .andExpect(jsonPath("$.data.totalAmount", equalTo(50_001)))
                .andExpect(jsonPath("$.data.myShareAmount", equalTo(25_000)))
                .andExpect(jsonPath("$.data.myPaymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.paymentDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.confirmedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.canceledAt").value(nullValue()))
                .andExpect(jsonPath("$.data.teamDeliveryAddress").value(nullValue()))
                .andExpect(jsonPath("$.data.serverTime", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.participants", hasSize(2)))
                .andExpect(jsonPath("$.data.participants[?(@.memberId == " + CREATOR + ")].creator", contains(true)))
                .andExpect(jsonPath("$.data.participants[?(@.memberId == " + CREATOR + ")].shareAmount", contains(25_001)))
                .andExpect(jsonPath("$.data.participants[?(@.memberId == " + CREATOR + ")].status", contains("UNPAID")))
                .andExpect(jsonPath("$.data.participants[?(@.memberId == " + CREATOR + ")].paidAt", contains(nullValue())))
                .andExpect(jsonPath("$.data.participants[?(@.memberId == " + CREATOR + ")].nickname", contains("회원" + CREATOR)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].itemId").isNumber())
                .andExpect(jsonPath("$.data.items[0].productId", equalTo(2)))
                .andExpect(jsonPath("$.data.items[0].inventoryId", equalTo(4)))
                .andExpect(jsonPath("$.data.items[0].productName").value("축구공"))
                .andExpect(jsonPath("$.data.items[0].optionName").value("5호"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://example.com/ball.png"))
                .andExpect(jsonPath("$.data.items[0].quantity", equalTo(1)))
                .andExpect(jsonPath("$.data.items[0].unitPrice", equalTo(50_001)))
                .andExpect(jsonPath("$.data.items[0].amount", equalTo(50_001)));

        ok(get("/api/group-orders/shared/{id}", order.id()).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.creator").value(true))
                .andExpect(jsonPath("$.data.myShareAmount", equalTo(25_001)));
    }

    @Test
    @DisplayName("공동 주문 분담금 결제는 참여자면 본문 없이, 생성자면 본문의 teamDeliveryAddress로 결제하고 결제 결과를 준다")
    void sharedOrderPayment() throws Exception {
        SharedOrder order = saveSharedOrder();

        ok(post("/api/group-orders/shared/{id}/payment", order.id()).with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data.paymentId").isNumber())
                .andExpect(jsonPath("$.data.orderId", equalTo(num(order.id()))))
                .andExpect(jsonPath("$.data.amount", equalTo(25_000)))
                .andExpect(jsonPath("$.data.type").value("SHARED_GROUP"))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        ok(json(post("/api/group-orders/shared/{id}/payment", order.id()), "{\"teamDeliveryAddress\":" + ADDRESS_JSON + "}")
                .with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.amount", equalTo(25_001)));

        ok(get("/api/group-orders/shared/{id}", order.id()).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.teamDeliveryAddress.receiverName").value("홍길동"))
                .andExpect(jsonPath("$.data.teamDeliveryAddress.receiverPhone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.teamDeliveryAddress.zipCode").value("12345"))
                .andExpect(jsonPath("$.data.teamDeliveryAddress.address").value("서울시 강남구"))
                .andExpect(jsonPath("$.data.teamDeliveryAddress.addressDetail").value("101호"))
                .andExpect(jsonPath("$.data.participants[?(@.memberId == " + MEMBER + ")].status", contains("PAID")))
                .andExpect(jsonPath("$.data.participants[?(@.memberId == " + MEMBER + ")].paidAt",
                        contains(matchesPattern(ISO_LOCAL_DATE_TIME))));
    }

    @Test
    @DisplayName("공동 주문 취소는 경로의 공동 주문 id로 생성자가 취소하고 data 없이 200을 준다")
    void sharedOrderCancel() throws Exception {
        SharedOrder order = saveSharedOrder();

        ok(post("/api/group-orders/shared/{id}/cancel", order.id()).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        ok(get("/api/group-orders/shared/{id}", order.id()).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.cancelReason").isString())
                .andExpect(jsonPath("$.data.canceledAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
    }

    @Test
    @DisplayName("어드민 페이백 지급 목록은 status 쿼리 파라미터(기본 FAILED)로 거르고, 실패 건을 다시 요청할 수 있다")
    void adminPaybackPayouts() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        PaybackPayout failed = PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, 77L,
                new PaybackSettlement.PayoutLine(BUYER, 5L, PaybackFunder.ADMIN, null, 2, 500L), now);
        failed.markFailed("WALLET_NOT_FOUND", false, 1, now);
        Long failedId = paybackPayoutRepository.save(failed).getId();
        Long requestedId = paybackPayoutRepository.save(PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, 77L,
                new PaybackSettlement.PayoutLine(OTHER_BUYER, 5L, PaybackFunder.ADMIN, null, 2, 500L), now)).getId();

        ok(get("/api/admin/orders/paybacks").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].paybackId", equalTo(num(failedId))))
                .andExpect(jsonPath("$.data[0].groupPurchaseType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.data[0].groupPurchaseId", equalTo(77)))
                .andExpect(jsonPath("$.data[0].memberId", equalTo(num(BUYER))))
                .andExpect(jsonPath("$.data[0].productId", equalTo(5)))
                .andExpect(jsonPath("$.data[0].funder").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].quantity", equalTo(2)))
                .andExpect(jsonPath("$.data[0].amountPerUnit", equalTo(500)))
                .andExpect(jsonPath("$.data[0].amount", equalTo(1_000)))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].attempt", equalTo(1)))
                .andExpect(jsonPath("$.data[0].failureCode").value("WALLET_NOT_FOUND"))
                .andExpect(jsonPath("$.data[0].requestedAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data[0].completedAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
        ok(get("/api/admin/orders/paybacks").param("status", "REQUESTED").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data[*].paybackId", contains(num(requestedId))));

        ok(post("/api/admin/orders/paybacks/{paybackId}/retry", failedId).with(admin(ADMIN)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        ok(get("/api/admin/orders/paybacks").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    private Long createPersonalOrder(long buyer) {
        cartFacade.addItem(buyer, 1L, 2);
        List<Long> cartItemIds = cartFacade.getCart(buyer).items().stream().map(CartItemResponse::cartItemId).toList();
        return orderFacade.createPersonal(buyer, new OrderCreateRequest(cartItemIds, ADDRESS)).orderId();
    }

    private SharedOrder saveSharedOrder() {
        LocalDateTime now = LocalDateTime.now();
        Long purchaseId = sharedGroupPurchaseRepository.save(sharedPurchase(CREATOR, MEMBER, now)).getId();
        SharedGroupOrder order = sharedOrder(purchaseId, CREATOR, MEMBER, now, sharedLine(2L, 4L, 7L, 1, 50_001L));
        order.markStockReserved();
        return new SharedOrder(sharedGroupOrderRepository.save(order).getId(), purchaseId);
    }

    private record SharedOrder(Long id, Long purchaseId) {
    }
}
