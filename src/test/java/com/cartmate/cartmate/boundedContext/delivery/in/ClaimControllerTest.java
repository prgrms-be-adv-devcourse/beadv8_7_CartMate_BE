package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 매핑·요청 변환·응답 형식·예외 변환만 검증한다. 인증 필터와 DB는 거치지 않는다.
class ClaimControllerTest {

    private static final String BODY = "{"
            + "\"deliveryId\":5,"
            + "\"claimType\":\"EXCHANGE\","
            + "\"reason\":\"SIZE_MISMATCH\","
            + "\"detail\":\"L로 교환 원합니다\","
            + "\"items\":[{\"deliveryItemId\":50,\"quantity\":1},{\"deliveryItemId\":51,\"quantity\":2}]"
            + "}";

    private final DeliveryFacade deliveryFacade = mock(DeliveryFacade.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ClaimController(deliveryFacade))
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
    @DisplayName("POST /api/claims는 201과 RsData로 응답하고 요청 본문을 로그인한 회원의 커맨드로 변환한다")
    void createClaim() throws Exception {
        DeliveryClaimResponse response = new DeliveryClaimResponse(
                7L, 5L, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다",
                ClaimStatus.REQUESTED, null,
                List.of(new DeliveryClaimResponse.Item(50L, 1000L, "티셔츠", 1L)));
        when(deliveryFacade.createClaim(any())).thenReturn(response);

        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.claimId").value(7))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.items[0].productName").value("티셔츠"));

        ArgumentCaptor<DeliveryClaimCreateCommand> captor = ArgumentCaptor.forClass(DeliveryClaimCreateCommand.class);
        verify(deliveryFacade).createClaim(captor.capture());
        DeliveryClaimCreateCommand command = captor.getValue();
        assertThat(command.deliveryId()).isEqualTo(5L);
        assertThat(command.memberId()).isEqualTo(1L);
        assertThat(command.claimType()).isEqualTo(ClaimType.EXCHANGE);
        assertThat(command.reason()).isEqualTo(ClaimReason.SIZE_MISMATCH);
        assertThat(command.detail()).isEqualTo("L로 교환 원합니다");
        assertThat(command.items())
                .extracting(DeliveryClaimCreateCommand.Item::deliveryItemId, DeliveryClaimCreateCommand.Item::quantity)
                .containsExactly(tuple(50L, 1L), tuple(51L, 2L));
    }

    @Test
    @DisplayName("GET /api/claims/me는 로그인한 회원의 클레임 목록과 진행 상태를 RsData로 응답한다")
    void myClaims() throws Exception {
        DeliveryClaimResponse response = new DeliveryClaimResponse(
                7L, 5L, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다",
                ClaimStatus.APPROVED, null,
                List.of(new DeliveryClaimResponse.Item(50L, 1000L, "티셔츠", 1L)));
        when(deliveryFacade.getMyClaims(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/claims/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.content[0].claimId").value(7))
                .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.content[0].claimType").value("EXCHANGE"))
                .andExpect(jsonPath("$.data.content[0].items[0].productName").value("티셔츠"));
        verify(deliveryFacade).getMyClaims(eq(1L), any());
    }

    @Test
    @DisplayName("GET /api/claims/me는 page와 size 파라미터를 페이징 정보로 전달한다")
    void myClaimsPaging() throws Exception {
        when(deliveryFacade.getMyClaims(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mvc.perform(get("/api/claims/me").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryFacade).getMyClaims(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("GET /api/claims/seller는 주문번호와 상품을 포함한 목록을 RsData로 응답한다")
    void sellerClaims() throws Exception {
        SellerClaimResponse row = new SellerClaimResponse(
                7L, 5L, 100L, ClaimType.REFUND, ClaimReason.PRODUCT_DEFECT, "마감 불량입니다",
                ClaimStatus.REQUESTED, null,
                List.of(new DeliveryClaimResponse.Item(50L, 1000L, "티셔츠", 1L)));
        when(deliveryFacade.getSellerClaims(eq(1L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/claims/seller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.content[0].claimId").value(7))
                .andExpect(jsonPath("$.data.content[0].orderId").value(100))
                .andExpect(jsonPath("$.data.content[0].claimType").value("REFUND"))
                .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.content[0].items[0].productName").value("티셔츠"));
    }

    @Test
    @DisplayName("status는 쉼표로 여러 개, claimType은 하나를 받고 생략하면 null로 전달한다")
    @SuppressWarnings("unchecked")
    void sellerClaimsFilters() throws Exception {
        when(deliveryFacade.getSellerClaims(eq(1L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/claims/seller").param("status", "REQUESTED,APPROVED").param("claimType", "EXCHANGE"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/claims/seller")).andExpect(status().isOk());

        ArgumentCaptor<List<ClaimStatus>> statuses = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ClaimType> types = ArgumentCaptor.forClass(ClaimType.class);
        verify(deliveryFacade, org.mockito.Mockito.times(2))
                .getSellerClaims(eq(1L), statuses.capture(), types.capture(), any());
        assertThat(statuses.getAllValues().get(0)).containsExactly(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);
        assertThat(types.getAllValues().get(0)).isEqualTo(ClaimType.EXCHANGE);
        assertThat(statuses.getAllValues().get(1)).isNull();
        assertThat(types.getAllValues().get(1)).isNull();
    }

    @Test
    @DisplayName("판매자가 아니면 판매자 목록은 403으로 변환된다")
    void sellerClaimsForbidden() throws Exception {
        when(deliveryFacade.getSellerClaims(eq(1L), any(), any(), any()))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_SELLER_ONLY));

        mvc.perform(get("/api/claims/seller"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-DELIVERY_SELLER_ONLY"));
    }

    @Test
    @DisplayName("PATCH /api/claims/{claimId}/approve는 본문 없이 수락 처리하고 RsData로 응답한다")
    void approveClaim() throws Exception {
        SellerClaimResponse response = new SellerClaimResponse(
                7L, 5L, 100L, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다",
                ClaimStatus.APPROVED, null,
                List.of(new DeliveryClaimResponse.Item(50L, 1000L, "티셔츠", 1L)));
        when(deliveryFacade.approveClaim(7L, 1L)).thenReturn(response);

        mvc.perform(patch("/api/claims/7/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.claimId").value(7))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.orderId").value(100));
    }

    @Test
    @DisplayName("수락에서 권한 없음 403, 상태 불일치 409, 없는 클레임 404로 변환된다")
    void approveClaimErrors() throws Exception {
        when(deliveryFacade.approveClaim(6L, 1L))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED));
        when(deliveryFacade.approveClaim(7L, 1L))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_INVALID_STATUS));
        when(deliveryFacade.approveClaim(8L, 1L))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_NOT_FOUND));

        mvc.perform(patch("/api/claims/6/approve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-DELIVERY_ACCESS_DENIED"));
        mvc.perform(patch("/api/claims/7/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-DELIVERY_CLAIM_INVALID_STATUS"));
        mvc.perform(patch("/api/claims/8/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-DELIVERY_CLAIM_NOT_FOUND"));
    }

    @Test
    @DisplayName("회원 ID는 요청 본문이 아니라 로그인한 사용자에서만 가져온다")
    void memberIdComesFromLogin() throws Exception {
        when(deliveryFacade.createClaim(any())).thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryId\":5,\"memberId\":999,\"claimType\":\"REFUND\",\"reason\":\"OTHER\","
                        + "\"items\":[{\"deliveryItemId\":50,\"quantity\":1}]}"));

        ArgumentCaptor<DeliveryClaimCreateCommand> captor = ArgumentCaptor.forClass(DeliveryClaimCreateCommand.class);
        verify(deliveryFacade).createClaim(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("items가 없는 요청도 컨트롤러에서 막지 않고 유스케이스 검증에 맡긴다")
    void nullItemsPassedThrough() throws Exception {
        when(deliveryFacade.createClaim(any()))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_ITEMS_REQUIRED));

        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryId\":5,\"claimType\":\"REFUND\",\"reason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-DELIVERY_CLAIM_ITEMS_REQUIRED"));
    }

    @Test
    @DisplayName("권한 없음 403, 상태 불일치와 중복 접수 409, 없는 배송 404로 변환된다")
    void errorsAreMapped() throws Exception {
        when(deliveryFacade.createClaim(any()))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_ALREADY_EXISTS))
                .thenThrow(new ServiceException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-DELIVERY_ACCESS_DENIED"));
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-DELIVERY_INVALID_STATUS"));
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-DELIVERY_CLAIM_ALREADY_EXISTS"));
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-DELIVERY_NOT_FOUND"));
    }
}
