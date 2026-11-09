package com.cartmate.cartmate.boundedContext.orderService;

import com.cartmate.cartmate.global.security.BlockedMemberRegistry;
import com.cartmate.cartmate.global.security.JwtProvider;
import com.cartmate.cartmate.global.security.WithdrawalPendingRegistry;
import com.cartmate.cartmate.support.ExternalApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExternalApiGuardTest extends ExternalApiTestSupport {

    private static final long MEMBER = 600_000_001L;

    @Value("${custom.jwt.secret}")
    private String jwtSecret;

    @Autowired
    private BlockedMemberRegistry blockedMemberRegistry;

    @Autowired
    private WithdrawalPendingRegistry withdrawalPendingRegistry;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /api/carts",
            "GET, /api/carts/matches",
            "POST, /api/group-purchases/shared",
            "POST, /api/group-purchases/individual",
            "POST, /api/orders",
            "GET, /api/group-orders/shared/me",
            "GET, /api/admin/carts/demands",
            "POST, /api/admin/carts/group-purchases/individual/1/close",
            "GET, /api/admin/abuse/members",
            "GET, /api/admin/abuse/members/1"
    })
    @DisplayName("로그인하지 않고 부르면 컨트롤러에 닿기 전에 401과 인증 필요 응답을 준다")
    void unauthenticated(String method, String path) throws Exception {
        expectUnauthorized(mvc.perform(request(HttpMethod.valueOf(method), path)));
    }

    @Test
    @DisplayName("서명이 다르거나 만료된 토큰은 로그인하지 않은 것으로 보고 401을 준다")
    void invalidToken() throws Exception {
        String forged = new JwtProvider("another-secret-key-that-is-long-enough-for-hs256", 1800)
                .createAccessToken(MEMBER, "member@cartmate.test", "BUYER", null);
        String expired = new JwtProvider(jwtSecret, -60)
                .createAccessToken(MEMBER, "member@cartmate.test", "BUYER", null);

        expectUnauthorized(mvc.perform(get("/api/carts").with(bearer(forged))));
        expectUnauthorized(mvc.perform(get("/api/carts").with(bearer(expired))));
    }

    @Test
    @DisplayName("정지된 회원은 남은 토큰이 유효해도 401을 받는다")
    void blockedMember() throws Exception {
        blockedMemberRegistry.block(MEMBER);
        try {
            expectUnauthorized(mvc.perform(get("/api/carts").with(buyer(MEMBER))));
        } finally {
            blockedMemberRegistry.unblock(MEMBER);
        }
    }

    @Test
    @DisplayName("탈퇴 대기 회원은 조회는 할 수 있지만 장바구니 · 주문 변경 요청은 403으로 막힌다")
    void withdrawalPendingMember() throws Exception {
        withdrawalPendingRegistry.add(MEMBER);
        try {
            ok(get("/api/carts").with(buyer(MEMBER)));
            mvc.perform(json(post("/api/carts/items"), "{\"inventoryId\":1,\"quantity\":1}").with(buyer(MEMBER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.resultCode").value("403-MEMBER_WITHDRAWAL_PENDING"));
        } finally {
            withdrawalPendingRegistry.remove(MEMBER);
        }
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @CsvSource({
            "BUYER, GET, /api/admin/carts/demands",
            "SELLER, POST, /api/admin/carts/demands/1/group-purchases",
            "BUYER, POST, /api/admin/carts/group-purchases/individual/1/close",
            "SELLER, GET, /api/admin/carts/group-purchases/individual/1/dashboard",
            "BUYER, GET, /api/admin/abuse/members",
            "SELLER, GET, /api/admin/abuse/members/1"
    })
    @DisplayName("어드민 API를 구매자 · 판매자가 부르면 403과 권한 없음 응답을 준다")
    void adminOnly(String role, String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).with(loggedIn(MEMBER, role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-AUTH_FORBIDDEN"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @ParameterizedTest(name = "{1} {2} → {4}")
    @MethodSource("invalidRequests")
    @DisplayName("요청 본문이 검증 규칙을 어기면 400과 공통 입력 오류 응답을 주고, 메시지에 어긴 필드를 담는다")
    void invalidRequest(boolean asAdmin, String method, String path, String body, String field) throws Exception {
        RequestPostProcessor user = asAdmin ? admin(MEMBER) : buyer(MEMBER);
        mvc.perform(json(request(HttpMethod.valueOf(method), path), body).with(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.msg", containsString(field + " : ")));
    }

    static Stream<Arguments> invalidRequests() {
        String address = "{\"receiverName\":\"홍길동\",\"receiverPhone\":\"010-1234-5678\",\"zipCode\":\"12345\",\"address\":\"서울시\"}";
        return Stream.of(
                Arguments.of(false, "POST", "/api/carts/items", "{\"inventoryId\":1,\"quantity\":1000}", "quantity"),
                Arguments.of(false, "PATCH", "/api/carts/items/1", "{\"quantity\":0}", "quantity"),
                Arguments.of(false, "POST", "/api/group-purchases/shared", "{\"title\":\"모임\",\"maxParticipants\":1}", "maxParticipants"),
                Arguments.of(false, "POST", "/api/group-purchases/shared/join", "{\"inviteCode\":\"ABCDEFGHIJKL\"}", "inviteCode"),
                Arguments.of(false, "PATCH", "/api/group-purchases/shared/1/max-participants", "{\"maxParticipants\":101}", "maxParticipants"),
                Arguments.of(false, "POST", "/api/group-purchases/shared/1/items", "{\"inventoryId\":1,\"quantity\":1000}", "quantity"),
                Arguments.of(false, "PATCH", "/api/group-purchases/shared/1/items/1/quantity", "{\"quantity\":1000}", "quantity"),
                Arguments.of(false, "PATCH", "/api/group-purchases/shared/1/items/1/quantity/adjust", "{\"delta\":999}", "delta"),
                Arguments.of(false, "PATCH", "/api/group-purchases/shared/1/items/1/option", "{}", "inventoryId"),
                Arguments.of(false, "POST", "/api/group-purchases/individual", "{\"title\":\"모임\",\"productIds\":[]}", "productIds"),
                Arguments.of(false, "POST", "/api/group-purchases/individual/1/products", "{}", "productId"),
                Arguments.of(false, "PATCH", "/api/group-purchases/individual/1/deadline", "{\"deadlineAt\":\"2020-01-01T00:00:00\"}", "deadlineAt"),
                Arguments.of(true, "POST", "/api/admin/carts/demands/1/group-purchases", "{\"title\":\"\"}", "title"),
                Arguments.of(false, "POST", "/api/orders",
                        "{\"cartItemIds\":[1],\"deliveryAddress\":{\"receiverPhone\":\"010-1234-5678\",\"zipCode\":\"12345\",\"address\":\"서울시\"}}",
                        "deliveryAddress.receiverName"),
                Arguments.of(false, "POST", "/api/orders/individual-group",
                        "{\"individualGroupPurchaseId\":1,\"items\":[{\"inventoryId\":1,\"quantity\":1000}],\"deliveryAddress\":" + address + "}",
                        "items[0].quantity"));
    }

    private static void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
