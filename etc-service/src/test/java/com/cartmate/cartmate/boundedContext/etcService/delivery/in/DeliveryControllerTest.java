package com.cartmate.cartmate.boundedContext.etcService.delivery.in;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryRetryService;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.SecurityUser;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryControllerTest {

    private static final long MEMBER_ID = 70L;
    private static final String CANCEL_URL = "/api/deliveries/1/cancel";

    private final DeliveryFacade deliveryFacade = mock(DeliveryFacade.class);
    private final DeliveryRetryService deliveryRetryService = mock(DeliveryRetryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityUser user = new SecurityUser(MEMBER_ID, "seller@cartmate.test", "SELLER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new DeliveryController(deliveryFacade, deliveryRetryService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("판매자 사유 취소는 로그인한 회원과 고른 사유를 넘기고 취소된 배송을 돌려준다")
    void cancel() throws Exception {
        given(deliveryRetryService.cancelDeliveryBySeller(1L, MEMBER_ID, DeliveryCancelReason.SELLER_OUT_OF_STOCK))
                .willReturn(new DeliveryResponse(1L, 100L, "O-100", PurchaseType.INDIVIDUAL_GROUP, "수령인",
                        DeliveryStatus.CANCELED, null, null, LocalDateTime.of(2026, 11, 11, 9, 0)));

        mvc.perform(patch(CANCEL_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelReason\":\"SELLER_OUT_OF_STOCK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.deliveryId").value(1))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("사유가 없으면 400으로 거절하고 취소를 부르지 않는다")
    void missingReason() throws Exception {
        mvc.perform(patch(CANCEL_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        then(deliveryRetryService).should(never()).cancelDeliveryBySeller(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("이미 취소된 배송이면 409와 처음 사유를 담은 문구를 돌려준다")
    void alreadyCanceled() throws Exception {
        DeliveryErrorCode code = DeliveryErrorCode.DELIVERY_ALREADY_CANCELED;
        given(deliveryRetryService.cancelDeliveryBySeller(1L, MEMBER_ID, DeliveryCancelReason.SELLER_OTHER))
                .willThrow(new ServiceException(code.resultCode(), code.getMsg() + " (사유: 구매자 취소)"));

        mvc.perform(patch(CANCEL_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelReason\":\"SELLER_OTHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-DELIVERY_ALREADY_CANCELED"))
                .andExpect(jsonPath("$.msg").value("이미 취소된 배송입니다. (사유: 구매자 취소)"));
    }
}
