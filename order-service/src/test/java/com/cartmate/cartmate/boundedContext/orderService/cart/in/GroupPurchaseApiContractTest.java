package com.cartmate.cartmate.boundedContext.orderService.cart.in;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandGroupPurchaseOpenRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.support.ExternalApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.cartmate.cartmate.support.OutboxRelayHelper.relayUntilPublished;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class GroupPurchaseApiContractTest extends ExternalApiTestSupport {

    private static final long CREATOR = 620_000_001L;
    private static final long MEMBER = 620_000_002L;
    private static final long OTHER_MEMBER = 620_000_003L;
    private static final long ADMIN = 620_000_100L;
    private static final String INVITE_CODE = "[a-z0-9]{12}";
    private static final DeliveryAddressRequest ADDRESS =
            new DeliveryAddressRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Autowired
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Test
    @DisplayName("공동물품 공동구매를 만들고 초대 코드로 참여하면 상세를 계약된 필드 이름 · 타입으로 준다")
    void sharedCreateAndJoin() throws Exception {
        created(json(post("/api/group-purchases/shared"),
                "{\"title\":\"FC 훈련용품\",\"maxParticipants\":5}").with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.inviteCode", matchesPattern(INVITE_CODE)))
                .andExpect(jsonPath("$.data.excludedInventoryIds", hasSize(0)));
        Long id = sharedGroupPurchaseFacade.getMyList(CREATOR).get(0).id();
        String inviteCode = sharedGroupPurchaseFacade.getDetail(CREATOR, id).inviteCode();
        sharedGroupPurchaseFacade.addItem(CREATOR, id, 1L, 2);

        ok(json(post("/api/group-purchases/shared/join"), "{\"inviteCode\":\"" + inviteCode + "\"}").with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data.id", equalTo(num(id))))
                .andExpect(jsonPath("$.data.title").value("FC 훈련용품"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
                .andExpect(jsonPath("$.data.creatorMemberId", equalTo(num(CREATOR))))
                .andExpect(jsonPath("$.data.viewerIsCreator").value(false))
                .andExpect(jsonPath("$.data.maxParticipants", equalTo(5)))
                .andExpect(jsonPath("$.data.participantCount", equalTo(2)))
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.editDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.frozenAt").value(nullValue()))
                .andExpect(jsonPath("$.data.closedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.serverTime", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.totalPrice", equalTo(78_000)))
                .andExpect(jsonPath("$.data.perPersonAmount", equalTo(39_000)))
                .andExpect(jsonPath("$.data.creatorAmount", equalTo(39_000)))
                .andExpect(jsonPath("$.data.hasUnavailableItem").value(false))
                .andExpect(jsonPath("$.data.creatorNickname").value("회원" + CREATOR))
                .andExpect(jsonPath("$.data.members[*].memberId", containsInAnyOrder(num(CREATOR), num(MEMBER))))
                .andExpect(jsonPath("$.data.members[?(@.memberId == " + MEMBER + ")].creator", contains(false)))
                .andExpect(jsonPath("$.data.members[?(@.memberId == " + MEMBER + ")].joinedAt", contains(matchesPattern(ISO_LOCAL_DATE_TIME))))
                .andExpect(jsonPath("$.data.members[?(@.memberId == " + MEMBER + ")].nickname", contains("회원" + MEMBER)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].itemId").isNumber())
                .andExpect(jsonPath("$.data.items[0].productId", equalTo(1)))
                .andExpect(jsonPath("$.data.items[0].inventoryId", equalTo(1)))
                .andExpect(jsonPath("$.data.items[0].productName").value("축구 유니폼"))
                .andExpect(jsonPath("$.data.items[0].optionName").value("M / 레드"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://example.com/uniform.png"))
                .andExpect(jsonPath("$.data.items[0].price", equalTo(39_000)))
                .andExpect(jsonPath("$.data.items[0].quantity", equalTo(2)))
                .andExpect(jsonPath("$.data.items[0].totalPrice", equalTo(78_000)))
                .andExpect(jsonPath("$.data.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.data.items[0].updatedMemberId", equalTo(num(CREATOR))))
                .andExpect(jsonPath("$.data.items[0].updatedAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.items[0].updatedMemberNickname").value("회원" + CREATOR));

        ok(get("/api/group-purchases/shared/{id}", id).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.viewerIsCreator").value(true));
        ok(get("/api/group-purchases/shared/me").with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", equalTo(num(id))))
                .andExpect(jsonPath("$.data[0].title").value("FC 훈련용품"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[0].creator").value(false))
                .andExpect(jsonPath("$.data[0].participantCount", equalTo(2)))
                .andExpect(jsonPath("$.data[0].maxParticipants", equalTo(5)))
                .andExpect(jsonPath("$.data[0].editable").value(true))
                .andExpect(jsonPath("$.data[0].editDeadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
    }

    @Test
    @DisplayName("공동물품 상품 담기 · 수량 변경 · 증감 · 옵션 변경 · 삭제는 경로의 공동구매 id와 항목 id로 동작한다")
    void sharedItems() throws Exception {
        Long id = createShared().id();
        sharedGroupPurchaseFacade.join(MEMBER, sharedGroupPurchaseFacade.getDetail(CREATOR, id).inviteCode());

        ok(json(post("/api/group-purchases/shared/{id}/items", id), "{\"inventoryId\":1,\"quantity\":1}").with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        Long itemId = sharedGroupPurchaseFacade.getDetail(CREATOR, id).items().get(0).itemId();

        ok(json(patch("/api/group-purchases/shared/{id}/items/{itemId}/quantity", id, itemId), "{\"quantity\":2}")
                .with(buyer(CREATOR)));
        ok(json(patch("/api/group-purchases/shared/{id}/items/{itemId}/quantity/adjust", id, itemId), "{\"delta\":-1}")
                .with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data.itemId", equalTo(num(itemId))))
                .andExpect(jsonPath("$.data.quantity", equalTo(1)));
        ok(json(patch("/api/group-purchases/shared/{id}/items/{itemId}/option", id, itemId), "{\"inventoryId\":2}")
                .with(buyer(MEMBER)));
        ok(get("/api/group-purchases/shared/{id}", id).with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data.items[*].inventoryId", contains(2)));

        ok(delete("/api/group-purchases/shared/{id}/items/{itemId}", id, itemId).with(buyer(MEMBER)));
        ok(get("/api/group-purchases/shared/{id}", id).with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("공동물품 참여자 관리는 members/me면 나가기, members/{회원 id}면 내보내기로 갈리고 최대 인원은 본문으로 바꾼다")
    void sharedMembers() throws Exception {
        Long id = createShared().id();
        String inviteCode = sharedGroupPurchaseFacade.getDetail(CREATOR, id).inviteCode();
        sharedGroupPurchaseFacade.join(MEMBER, inviteCode);
        sharedGroupPurchaseFacade.join(OTHER_MEMBER, inviteCode);

        ok(json(patch("/api/group-purchases/shared/{id}/max-participants", id), "{\"maxParticipants\":6}").with(buyer(CREATOR)));
        ok(delete("/api/group-purchases/shared/{id}/members/me", id).with(buyer(MEMBER)));
        ok(delete("/api/group-purchases/shared/{id}/members/{targetMemberId}", id, OTHER_MEMBER).with(buyer(CREATOR)));

        ok(get("/api/group-purchases/shared/{id}", id).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.maxParticipants", equalTo(6)))
                .andExpect(jsonPath("$.data.members[*].memberId", contains(num(CREATOR))));
    }

    @Test
    @DisplayName("공동물품 마감 · 취소는 data 없이 200, 다시 만들기는 201과 새 공동구매 id · 초대 코드 · 빠진 옵션 목록을 준다")
    void sharedCloseCancelDuplicate() throws Exception {
        Long closing = createShared().id();
        sharedGroupPurchaseFacade.addItem(CREATOR, closing, 1L, 1);
        ok(post("/api/group-purchases/shared/{id}/close", closing).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data").value(nullValue()));

        Long canceling = createShared().id();
        sharedGroupPurchaseFacade.addItem(CREATOR, canceling, 4L, 1);
        ok(post("/api/group-purchases/shared/{id}/cancel", canceling).with(buyer(CREATOR)));

        created(post("/api/group-purchases/shared/{id}/duplicate", canceling).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.inviteCode", matchesPattern(INVITE_CODE)))
                .andExpect(jsonPath("$.data.excludedInventoryIds", hasSize(0)));
    }

    @Test
    @DisplayName("개인물품 공동구매를 만들고 목록 · 초대 코드 · id로 조회하면 계약된 필드 이름 · 타입으로 준다")
    void individualCreateAndRead() throws Exception {
        created(json(post("/api/group-purchases/individual"), "{\"title\":\"OO FC 유니폼\",\"productIds\":[1,2]}")
                .with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.inviteCode", matchesPattern(INVITE_CODE)));
        Long id = individualGroupPurchaseFacade.getMyList(CREATOR).get(0).id();
        String inviteCode = individualGroupPurchaseFacade.getDetail(CREATOR, id).inviteCode();

        ok(get("/api/group-purchases/individual/me").with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", equalTo(num(id))))
                .andExpect(jsonPath("$.data[0].title").value("OO FC 유니폼"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[0].open").value(true))
                .andExpect(jsonPath("$.data[0].productCount", equalTo(2)))
                .andExpect(jsonPath("$.data[0].deadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)));

        ok(get("/api/group-purchases/individual/invite/{inviteCode}", inviteCode).with(buyer(MEMBER)))
                .andExpect(jsonPath("$.data.id", equalTo(num(id))))
                .andExpect(jsonPath("$.data.title").value("OO FC 유니폼"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
                .andExpect(jsonPath("$.data.viewerIsCreator").value(false))
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.deadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.closedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.serverTime", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.products[*].productId", containsInAnyOrder(1, 2)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].productName", contains("축구공")))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].imageUrl", contains("https://example.com/ball.png")))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].purchasable", contains(true)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].inventoryId", contains(4)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].optionName", contains("5호")))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].price", contains(25_000)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].purchasable", contains(true)));

        ok(get("/api/group-purchases/individual/{id}", id).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.viewerIsCreator").value(true));
    }

    @Test
    @DisplayName("개인물품 현황판은 결제된 주문을 상품 · 옵션 · 구매자별로 묶어 계약된 필드로 준다")
    void individualDashboard() throws Exception {
        Long id = createIndividual(List.of(1L, 2L)).id();
        Long orderId = orderFacade.createIndividualGroup(MEMBER, new IndividualGroupOrderCreateRequest(
                id, List.of(new IndividualGroupOrderCreateRequest.Item(4L, 2)), ADDRESS)).orderId();
        orderFacade.applyPayment(orderId, MEMBER, orderId, LocalDateTime.now());
        relayUntilPublished(jdbcTemplate, outboxRelay, "Order", orderId);

        ok(get("/api/group-purchases/individual/{id}/dashboard", id).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.id", equalTo(num(id))))
                .andExpect(jsonPath("$.data.title").value("OO FC 유니폼"))
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.deadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.closedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.orderCount", equalTo(1)))
                .andExpect(jsonPath("$.data.buyerCount", equalTo(1)))
                .andExpect(jsonPath("$.data.totalQuantity", equalTo(2)))
                .andExpect(jsonPath("$.data.totalAmount", equalTo(50_000)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].productName", contains("축구공")))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].quantity", contains(2)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].amount", contains(50_000)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].inventoryId", contains(4)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].optionName", contains("5호")))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].quantity", contains(2)))
                .andExpect(jsonPath("$.data.products[?(@.productId == 2)].options[*].amount", contains(50_000)))
                .andExpect(jsonPath("$.data.buyers", hasSize(1)))
                .andExpect(jsonPath("$.data.buyers[0].memberId", equalTo(num(MEMBER))))
                .andExpect(jsonPath("$.data.buyers[0].nickname").value("회원" + MEMBER))
                .andExpect(jsonPath("$.data.buyers[0].quantity", equalTo(2)))
                .andExpect(jsonPath("$.data.buyers[0].amount", equalTo(50_000)))
                .andExpect(jsonPath("$.data.buyers[0].lines", hasSize(1)))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].orderId", equalTo(num(orderId))))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].productId", equalTo(2)))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].productName").value("축구공"))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].inventoryId", equalTo(4)))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].optionName").value("5호"))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].quantity", equalTo(2)))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].amount", equalTo(50_000)))
                .andExpect(jsonPath("$.data.buyers[0].lines[0].orderedAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
    }

    @Test
    @DisplayName("개인물품 판매 상품 추가 · 빼기 · 마감 시각 변경 · 마감은 경로의 id와 본문 값으로 동작하고 data 없이 200을 준다")
    void individualEdit() throws Exception {
        Long id = createIndividual(List.of(1L)).id();
        String deadlineAt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .format(LocalDateTime.now().plusHours(5).truncatedTo(ChronoUnit.SECONDS));

        ok(json(post("/api/group-purchases/individual/{id}/products", id), "{\"productId\":2}").with(buyer(CREATOR)));
        ok(delete("/api/group-purchases/individual/{id}/products/{productId}", id, 1L).with(buyer(CREATOR)));
        ok(json(patch("/api/group-purchases/individual/{id}/deadline", id), "{\"deadlineAt\":\"" + deadlineAt + "\"}")
                .with(buyer(CREATOR)));
        ok(get("/api/group-purchases/individual/{id}", id).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.products[*].productId", contains(2)))
                .andExpect(jsonPath("$.data.deadlineAt").value(deadlineAt));

        ok(post("/api/group-purchases/individual/{id}/close", id).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        ok(get("/api/group-purchases/individual/{id}", id).with(buyer(CREATOR)))
                .andExpect(jsonPath("$.data.open").value(false));
    }

    @Test
    @DisplayName("어드민은 수요 공동구매의 현황판을 조회하고 마감할 수 있다")
    void adminIndividual() throws Exception {
        Long id = individualGroupPurchaseFacade.openFromDemand(ADMIN, 2L,
                new DemandGroupPurchaseOpenRequest("축구공 공동구매", null, null, null)).id();

        ok(get("/api/admin/carts/group-purchases/individual/{id}/dashboard", id).with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.id", equalTo(num(id))))
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.buyers", hasSize(0)));

        ok(post("/api/admin/carts/group-purchases/individual/{id}/close", id).with(admin(ADMIN)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        ok(get("/api/admin/carts/group-purchases/individual/{id}/dashboard", id).with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.open").value(false))
                .andExpect(jsonPath("$.data.closedAt", matchesPattern(ISO_LOCAL_DATE_TIME)));
    }

    private SharedGroupPurchaseCreateResponse createShared() {
        return sharedGroupPurchaseFacade.create(CREATOR, new SharedGroupPurchaseCreateRequest("FC 훈련용품", 5));
    }

    private IndividualGroupPurchaseCreateResponse createIndividual(List<Long> productIds) {
        return individualGroupPurchaseFacade.create(CREATOR, new IndividualGroupPurchaseCreateRequest("OO FC 유니폼", productIds));
    }
}
