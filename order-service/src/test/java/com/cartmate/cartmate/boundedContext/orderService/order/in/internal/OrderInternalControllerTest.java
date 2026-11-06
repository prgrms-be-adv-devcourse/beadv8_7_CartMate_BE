package com.cartmate.cartmate.boundedContext.orderService.order.in.internal;

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
class OrderInternalControllerTest {

    private static final long MEMBER_WITHOUT_ORDERS = 987_654_321L;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("주문이 없는 회원이면 목록은 비어 있고 수는 0이다")
    void emptyMember() throws Exception {
        mvc.perform(get("/internal/orders").param("memberId", String.valueOf(MEMBER_WITHOUT_ORDERS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/internal/orders/payment-pending-count").param("memberId", String.valueOf(MEMBER_WITHOUT_ORDERS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
        mvc.perform(get("/internal/group-orders/shared").param("memberId", String.valueOf(MEMBER_WITHOUT_ORDERS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/internal/group-orders/shared/confirmed-ids").param("memberId", String.valueOf(MEMBER_WITHOUT_ORDERS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/internal/group-orders/shared/paying-count").param("memberId", String.valueOf(MEMBER_WITHOUT_ORDERS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }
}
