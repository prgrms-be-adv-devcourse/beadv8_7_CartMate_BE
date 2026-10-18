package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
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
