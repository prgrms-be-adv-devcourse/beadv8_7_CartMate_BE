package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerDeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 매핑·응답 형식·예외 변환만 검증한다. 인증 필터와 DB는 거치지 않는다.
class DeliveryControllerTest {

    private final DeliveryFacade deliveryFacade = mock(DeliveryFacade.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DeliveryController(deliveryFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityUser(1L, "a@a.com", "USER"), null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 배송 목록은 RsData로 감싸서 내려가고 로그인한 회원 ID로 조회한다")
    void myDeliveries() throws Exception {
        DeliveryResponse response = new DeliveryResponse(5L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", DeliveryStatus.PREPARING, null, null, null);
        when(deliveryFacade.getMyDeliveries(eq(1L), any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/deliveries/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.content[0].deliveryId").value(5))
                .andExpect(jsonPath("$.data.content[0].receiverName").value("홍길동"));
        verify(deliveryFacade).getMyDeliveries(eq(1L), any());
    }

    @Test
    @DisplayName("판매자 목록은 status를 생략하면 배송요청 상태로 조회하고 status로 탭을 바꿀 수 있다")
    void sellerDeliveries() throws Exception {
        SellerDeliveryResponse row = new SellerDeliveryResponse(5L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                DeliveryStatus.REQUESTED, null, "홍길동", "010-0000-0000", "12345", "서울", "101호", null, null,
                List.of(new DeliveryDetailResponse.Item(1000L, "티셔츠", 2L,
                        List.of(new DeliveryDetailResponse.Option("사이즈", "L")))));
        when(deliveryFacade.getSellerDeliveries(eq(1L), eq(DeliveryStatus.REQUESTED), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
        when(deliveryFacade.getSellerDeliveries(eq(1L), eq(DeliveryStatus.SHIPPING), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/deliveries/seller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.content[0].orderId").value(100))
                .andExpect(jsonPath("$.data.content[0].receiverPhone").value("010-0000-0000"))
                .andExpect(jsonPath("$.data.content[0].items[0].productName").value("티셔츠"))
                .andExpect(jsonPath("$.data.content[0].items[0].options[0].optionValue").value("L"));
        mvc.perform(get("/api/deliveries/seller").param("status", "SHIPPING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("판매자가 아니면 판매자 목록은 403으로 변환된다")
    void sellerDeliveriesForbidden() throws Exception {
        when(deliveryFacade.getSellerDeliveries(eq(1L), any(), any()))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_SELLER_ONLY));

        mvc.perform(get("/api/deliveries/seller"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-DELIVERY_SELLER_ONLY"));
    }

    @Test
    @DisplayName("PATCH ready는 요청 본문의 택배사와 송장번호로 준비 처리하고 RsData로 응답한다")
    void readyDelivery() throws Exception {
        DeliveryResponse response = new DeliveryResponse(5L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", DeliveryStatus.PREPARING, "CJ대한통운", "658219384910", null);
        when(deliveryFacade.readyDelivery(5L, 1L, "CJ대한통운", "658219384910")).thenReturn(response);

        mvc.perform(patch("/api/deliveries/5/ready")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"658219384910\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.carrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.trackingNumber").value("658219384910"));
    }

    @Test
    @DisplayName("PATCH ready에서 상태가 맞지 않으면 409로 변환된다")
    void readyDeliveryConflict() throws Exception {
        when(deliveryFacade.readyDelivery(eq(6L), eq(1L), any(), any()))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS));

        mvc.perform(patch("/api/deliveries/6/ready")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-DELIVERY_INVALID_STATUS"));
    }

    @Test
    @DisplayName("배송 상세는 RsData로 감싸서 상품명과 옵션을 내려준다")
    void deliveryDetail() throws Exception {
        DeliveryDetailResponse response = new DeliveryDetailResponse(
                5L, 1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP, DeliveryStatus.PREPARING,
                null, null, "홍길동", "010-0000-0000", "12345", "서울", "101호",
                null, null, null, null, null,
                List.of(new DeliveryDetailResponse.Item(1000L, "티셔츠", 2L,
                        List.of(new DeliveryDetailResponse.Option("사이즈", "L")))));
        when(deliveryFacade.getDeliveryDetails(5L, 1L)).thenReturn(response);

        mvc.perform(get("/api/deliveries/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.items[0].productName").value("티셔츠"))
                .andExpect(jsonPath("$.data.items[0].options[0].optionName").value("사이즈"))
                .andExpect(jsonPath("$.data.items[0].options[0].optionValue").value("L"));
    }

    @Test
    @DisplayName("권한이 없으면 403, 없는 배송이면 404로 변환된다")
    void errorsAreMapped() throws Exception {
        when(deliveryFacade.getDeliveryDetails(6L, 1L))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED));
        when(deliveryFacade.getDeliveryDetails(7L, 1L))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        mvc.perform(get("/api/deliveries/6"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-DELIVERY_ACCESS_DENIED"));
        mvc.perform(get("/api/deliveries/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-DELIVERY_NOT_FOUND"));
    }
}
