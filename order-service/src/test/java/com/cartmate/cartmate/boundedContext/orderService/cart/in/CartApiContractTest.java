package com.cartmate.cartmate.boundedContext.orderService.cart.in;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandGroupPurchaseOpenRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.support.ExternalApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartApiContractTest extends ExternalApiTestSupport {

    private static final long BUYER = 610_000_001L;
    private static final long OTHER_BUYER = 610_000_002L;
    private static final long ADMIN = 610_000_100L;

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @Test
    @DisplayName("장바구니 조회는 담은 상품과 합계를 계약된 필드 이름 · 타입으로 준다")
    void getCart() throws Exception {
        ok(json(post("/api/carts/items"), "{\"inventoryId\":1,\"quantity\":2}").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        cartFacade.addItem(BUYER, 4L, 1);

        ok(get("/api/carts").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.cartId").isNumber())
                .andExpect(jsonPath("$.data.totalPrice", equalTo(103_000)))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].cartItemId").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].productId", contains(1)))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].productName", contains("축구 유니폼")))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].optionName", contains("M / 레드")))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].imageUrl", contains("https://example.com/uniform.png")))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].price", contains(39_000)))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].quantity", contains(2)))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].totalPrice", contains(78_000)))
                .andExpect(jsonPath("$.data.items[?(@.inventoryId == 1)].purchasable", contains(true)));
    }

    @Test
    @DisplayName("장바구니 상품의 수량 변경 · 삭제 · 비우기는 경로의 장바구니 상품 id로 동작하고 data 없이 200을 준다")
    void changeAndRemoveItems() throws Exception {
        cartFacade.addItem(BUYER, 1L, 1);
        cartFacade.addItem(BUYER, 4L, 1);
        List<CartItemResponse> items = cartFacade.getCart(BUYER).items();

        ok(json(patch("/api/carts/items/{cartItemId}", items.get(0).cartItemId()), "{\"quantity\":3}").with(buyer(BUYER)));
        ok(delete("/api/carts/items/{cartItemId}", items.get(1).cartItemId()).with(buyer(BUYER)));
        ok(get("/api/carts").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.items[*].quantity", contains(3)));

        ok(delete("/api/carts/items").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data").value(nullValue()));
        ok(get("/api/carts").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("없는 상품 옵션을 담으면 업무 오류의 상태 코드와 resultCode로 응답하고 data는 null이다")
    void businessError() throws Exception {
        mvc.perform(json(post("/api/carts/items"), "{\"inventoryId\":999,\"quantity\":1}").with(buyer(BUYER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-CART_PRODUCT_OPTION_NOT_FOUND"))
                .andExpect(jsonPath("$.msg").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("장바구니 매칭은 내 장바구니 상품으로 참여할 수 있는 어드민 공동구매를 계약된 필드로 준다")
    void matches() throws Exception {
        Long purchaseId = individualGroupPurchaseFacade.openFromDemand(ADMIN, 1L,
                new DemandGroupPurchaseOpenRequest("유니폼 공동구매", null, 5, 2_000L, null)).id();
        cartFacade.addItem(BUYER, 2L, 1);

        ok(get("/api/carts/matches").with(buyer(BUYER)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].individualGroupPurchaseId", equalTo(num(purchaseId))))
                .andExpect(jsonPath("$.data[0].title").value("유니폼 공동구매"))
                .andExpect(jsonPath("$.data[0].inviteCode", matchesPattern("[a-z0-9]{12}")))
                .andExpect(jsonPath("$.data[0].deadlineAt", matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data[0].paybackMinQuantity", equalTo(5)))
                .andExpect(jsonPath("$.data[0].paybackAmountPerUnit", equalTo(2_000)))
                .andExpect(jsonPath("$.data[0].productId", equalTo(1)))
                .andExpect(jsonPath("$.data[0].productName").value("축구 유니폼"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://example.com/uniform.png"))
                .andExpect(jsonPath("$.data[0].similarity", equalTo(1.0)))
                .andExpect(jsonPath("$.data[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.data[0].matchedCartProductIds", contains(1)));
    }

    @Test
    @DisplayName("어드민 수요 후보 조회는 쿼리 파라미터 이름대로 기준을 받고, 후보를 계약된 필드로 준다")
    void demandCandidates() throws Exception {
        cartFacade.addItem(BUYER, 4L, 1);
        cartFacade.addItem(OTHER_BUYER, 4L, 2);

        ok(get("/api/admin/carts/demands").with(admin(ADMIN))
                .param("minExactMembers", "2")
                .param("minMembers", "2")
                .param("days", "1")
                .param("size", "5")
                .param("cooldownDays", "0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].productId", equalTo(2)))
                .andExpect(jsonPath("$.data[0].productName").value("축구공"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://example.com/ball.png"))
                .andExpect(jsonPath("$.data[0].purchasable").value(true))
                .andExpect(jsonPath("$.data[0].openGroupPurchaseId").value(nullValue()))
                .andExpect(jsonPath("$.data[0].exactMemberCount", equalTo(2)))
                .andExpect(jsonPath("$.data[0].similarMemberCount", equalTo(0)))
                .andExpect(jsonPath("$.data[0].totalMemberCount", equalTo(2)))
                .andExpect(jsonPath("$.data[0].similarProducts", hasSize(0)));

        ok(get("/api/admin/carts/demands").with(admin(ADMIN)).param("minExactMembers", "3"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("어드민 수요 공동구매 열기는 경로의 상품 id로 열고 201과 공동구매 id · 초대 코드를 준다")
    void openDemandGroupPurchase() throws Exception {
        created(json(post("/api/admin/carts/demands/{productId}/group-purchases", 2L),
                "{\"title\":\"축구공 공동구매\",\"paybackMinQuantity\":3,\"paybackAmountPerUnit\":500}").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.inviteCode", matchesPattern("[a-z0-9]{12}")));
    }
}
