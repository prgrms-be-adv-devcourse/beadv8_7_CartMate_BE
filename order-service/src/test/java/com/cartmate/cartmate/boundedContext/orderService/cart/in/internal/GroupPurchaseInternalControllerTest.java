package com.cartmate.cartmate.boundedContext.orderService.cart.in.internal;

import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CartMateIntegrationTest
class GroupPurchaseInternalControllerTest {

    private static final long MEMBER_WITHOUT_PURCHASES = 987_654_322L;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("공동구매가 없는 회원이면 목록은 비어 있고 모집 중 수는 0이다")
    void emptyMember() throws Exception {
        mvc.perform(get("/internal/group-purchases/shared").param("memberId", String.valueOf(MEMBER_WITHOUT_PURCHASES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/internal/group-purchases/individual").param("memberId", String.valueOf(MEMBER_WITHOUT_PURCHASES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/internal/group-purchases/active-count").param("memberId", String.valueOf(MEMBER_WITHOUT_PURCHASES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }
}
