package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CartMateIntegrationTest
public abstract class ExternalApiTestSupport {

    protected static final String ISO_LOCAL_DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtProvider jwtProvider;

    protected MockMvc mvc;

    @BeforeEach
    void setUpExternalApi() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected RequestPostProcessor buyer(long memberId) {
        return loggedIn(memberId, "BUYER");
    }

    protected RequestPostProcessor admin(long memberId) {
        return loggedIn(memberId, "ADMIN");
    }

    protected RequestPostProcessor loggedIn(long memberId, String role) {
        // 관리자 API는 OTP를 거친 토큰(amr에 otp)이어야 통과한다 (JwtAuthenticationFilter)
        java.util.List<String> amr = "ADMIN".equals(role) ? java.util.List.of("pwd", "otp") : java.util.List.of("pwd");
        String token = jwtProvider.createAccessToken(memberId, "member" + memberId + "@cartmate.test", role, null,
                amr, jwtProvider.getAccessTokenExpireSeconds());
        return bearer(token);
    }

    protected static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected ResultActions ok(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));
    }

    protected ResultActions created(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"));
    }

    protected static int num(Long id) {
        return Math.toIntExact(id);
    }
}
