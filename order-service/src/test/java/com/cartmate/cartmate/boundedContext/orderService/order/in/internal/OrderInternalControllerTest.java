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

/**
 * 회원 서버가 부르는 주문 내부 API의 경로와 응답 모양(RsData 봉투)을 확인한다.
 * 보안 필터(내부 API 키)는 공용 설정이라 여기서는 빼고, 컨트롤러 계약만 본다.
 * 공용 컨텍스트(@CartMateIntegrationTest)를 그대로 쓰려고 MockMvc를 컨텍스트에서 직접 만든다.
 */
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
