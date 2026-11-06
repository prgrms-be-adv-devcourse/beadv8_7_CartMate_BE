package com.cartmate.cartmate.boundedContext.orderService.order.in.internal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.address;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.individualGroupOrder;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.productALine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.productBLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CartMateIntegrationTest
class OrderInternalControllerTest {

    private static final long BUYER = 700_000_001L;
    private static final long OTHER_BUYER = 700_000_002L;
    private static final long CREATOR = 700_000_011L;
    private static final long MEMBER = 700_000_012L;
    private static final long OTHER_CREATOR = 700_000_013L;
    private static final long OTHER_MEMBER = 700_000_014L;
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 3, 2, 10, 0);
    private static final String ISO_LOCAL_DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("주문 목록은 그 회원의 주문만 최신순으로 모든 상태를 담고, 각 항목의 필드 이름과 타입이 계약대로다")
    void orders() throws Exception {
        BuyerOrders orders = saveBuyerOrders();

        call("/internal/orders", BUYER)
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[*].id", contains(
                        num(orders.canceled()), num(orders.paid()), num(orders.pendingGroup()), num(orders.pendingPersonal()))))
                .andExpect(jsonPath("$.data[*].status", contains("CANCELED", "PAID", "PAYMENT_PENDING", "PAYMENT_PENDING")))
                .andExpect(jsonPath("$.data[2].orderType").value("INDIVIDUAL_GROUP"))
                .andExpect(jsonPath("$.data[3].orderNumber").value("20260302-PERSONAL"))
                .andExpect(jsonPath("$.data[3].orderType").value("PERSONAL"))
                .andExpect(jsonPath("$.data[3].totalAmount", equalTo(65_000)))
                .andExpect(jsonPath("$.data[3].itemCount", equalTo(2)))
                .andExpect(jsonPath("$.data[3].firstProductName").value("상품A"))
                .andExpect(jsonPath("$.data[3].createdAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
    }

    @Test
    @DisplayName("결제 대기 주문 수는 그 회원의 결제 대기 주문을 주문 종류와 상관없이 세고, 결제 완료 · 취소 주문은 세지 않는다")
    void paymentPendingCount() throws Exception {
        saveBuyerOrders();

        call("/internal/orders/payment-pending-count", BUYER)
                .andExpect(jsonPath("$.data", equalTo(2)));
    }

    @Test
    @DisplayName("공동 주문 목록은 참여한 공동 주문만 최신순으로 담고, 내 분담금과 내 결제 상태는 조회한 회원 기준이다")
    void sharedOrders() throws Exception {
        SharedOrders orders = saveSharedOrders();

        call("/internal/group-orders/shared", MEMBER)
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[*].id", contains(
                        num(orders.canceled()), num(orders.confirmed()), num(orders.payingMemberPaid()), num(orders.payingCreatorPaid()))))
                .andExpect(jsonPath("$.data[*].status", contains("CANCELED", "CONFIRMED", "PAYING", "PAYING")))
                .andExpect(jsonPath("$.data[3].sharedGroupPurchaseId", equalTo(101)))
                .andExpect(jsonPath("$.data[3].myShareAmount", equalTo(25_000)))
                .andExpect(jsonPath("$.data[3].myPaymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data[3].paidCount", equalTo(1)))
                .andExpect(jsonPath("$.data[3].participantCount", equalTo(2)))
                .andExpect(jsonPath("$.data[3].paymentDeadlineAt").value("2026-03-04T10:00:00"));

        call("/internal/group-orders/shared", CREATOR)
                .andExpect(jsonPath("$.data[3].id", equalTo(num(orders.payingCreatorPaid()))))
                .andExpect(jsonPath("$.data[3].myShareAmount", equalTo(25_001)))
                .andExpect(jsonPath("$.data[3].myPaymentStatus").value("PAID"));
    }

    @Test
    @DisplayName("확정된 공동 주문 id 목록은 참여한 공동 주문 중 전원 결제 확정된 것만 숫자 배열로 준다")
    void confirmedSharedOrderIds() throws Exception {
        SharedOrders orders = saveSharedOrders();

        call("/internal/group-orders/shared/confirmed-ids", MEMBER)
                .andExpect(jsonPath("$.data", contains(num(orders.confirmed()))));
    }

    @Test
    @DisplayName("결제 중인 공동 주문 수는 참여한 공동 주문이 결제 중이면 내가 이미 결제했어도 세고, 확정 · 취소는 세지 않는다")
    void payingSharedOrderCount() throws Exception {
        saveSharedOrders();

        call("/internal/group-orders/shared/paying-count", MEMBER)
                .andExpect(jsonPath("$.data", equalTo(2)));
    }

    @Test
    @DisplayName("주문이 없는 회원이면 목록 응답의 data는 null이 아닌 빈 배열이다")
    void emptyListsAreArrays() throws Exception {
        for (String path : List.of("/internal/orders", "/internal/group-orders/shared",
                "/internal/group-orders/shared/confirmed-ids")) {
            call(path, BUYER).andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    private ResultActions call(String path, long memberId) throws Exception {
        return mvc.perform(get(path).param("memberId", String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));
    }

    private BuyerOrders saveBuyerOrders() {
        LocalDateTime now = LocalDateTime.now();
        Long pendingPersonal = orderRepository.save(personalOrder("20260302-PERSONAL", BUYER,
                List.of(productALine(1L, 2), productBLine(2L, 1)), now)).getId();
        Long pendingGroup = orderRepository.save(individualGroupOrder("20260302-GROUP", BUYER, 31L,
                List.of(productALine(1L, 1)), now)).getId();
        Order paidOrder = personalOrder("20260302-PAID", BUYER, List.of(productALine(1L, 1)), now);
        paidOrder.markPaid(1L, now);
        Long paid = orderRepository.save(paidOrder).getId();
        Order canceledOrder = personalOrder("20260302-CANCELED", BUYER, List.of(productALine(1L, 1)), now);
        canceledOrder.cancelByBuyer(BUYER, now);
        Long canceled = orderRepository.save(canceledOrder).getId();
        orderRepository.save(personalOrder("20260302-OTHER", OTHER_BUYER, List.of(productALine(1L, 1)), now));
        return new BuyerOrders(pendingPersonal, pendingGroup, paid, canceled);
    }

    private SharedOrders saveSharedOrders() {
        SharedGroupOrder creatorPaid = newSharedOrder(101L, CREATOR, MEMBER);
        creatorPaid.pay(CREATOR, 1L, CLOSED_AT, address());
        Long payingCreatorPaid = sharedGroupOrderRepository.save(creatorPaid).getId();

        SharedGroupOrder memberPaid = newSharedOrder(102L, CREATOR, MEMBER);
        memberPaid.pay(MEMBER, 2L, CLOSED_AT, null);
        Long payingMemberPaid = sharedGroupOrderRepository.save(memberPaid).getId();

        SharedGroupOrder confirmedOrder = newSharedOrder(103L, CREATOR, MEMBER);
        confirmedOrder.pay(CREATOR, 3L, CLOSED_AT, address());
        confirmedOrder.pay(MEMBER, 4L, CLOSED_AT, null);
        Long confirmed = sharedGroupOrderRepository.save(confirmedOrder).getId();

        SharedGroupOrder canceledOrder = newSharedOrder(104L, CREATOR, MEMBER);
        canceledOrder.cancelByCreator(CREATOR, CLOSED_AT);
        Long canceled = sharedGroupOrderRepository.save(canceledOrder).getId();

        SharedGroupOrder othersConfirmed = newSharedOrder(105L, OTHER_CREATOR, OTHER_MEMBER);
        othersConfirmed.pay(OTHER_CREATOR, 5L, CLOSED_AT, address());
        othersConfirmed.pay(OTHER_MEMBER, 6L, CLOSED_AT, null);
        sharedGroupOrderRepository.save(othersConfirmed);
        sharedGroupOrderRepository.save(newSharedOrder(106L, OTHER_CREATOR, OTHER_MEMBER));

        return new SharedOrders(payingCreatorPaid, payingMemberPaid, confirmed, canceled);
    }

    private SharedGroupOrder newSharedOrder(Long sharedGroupPurchaseId, Long creator, Long member) {
        SharedGroupOrder order = sharedOrder(sharedGroupPurchaseId, creator, member, CLOSED_AT,
                sharedLine(2L, 4L, 7L, 1, 50_001L));
        order.markStockReserved();
        return order;
    }

    private static int num(Long id) {
        return Math.toIntExact(id);
    }

    private record BuyerOrders(Long pendingPersonal, Long pendingGroup, Long paid, Long canceled) {
    }

    private record SharedOrders(Long payingCreatorPaid, Long payingMemberPaid, Long confirmed, Long canceled) {
    }
}
