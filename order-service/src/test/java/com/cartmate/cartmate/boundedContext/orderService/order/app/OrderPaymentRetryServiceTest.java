package com.cartmate.cartmate.boundedContext.orderService.order.app;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderPaymentRetryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 15L;

    @Mock
    private OrderFacade orderFacade;
    @Mock
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    private OrderPaymentRetryService service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentRetryService(orderFacade, sharedGroupOrderFacade);
    }

    @Test
    @DisplayName("재고 부족으로 결제가 막히면 주문을 재고 부족으로 취소하고 오류를 그대로 던진다")
    void outOfStockCancelsOrder() {
        ServiceException outOfStock = new ServiceException(OrderErrorCode.ORDER_OUT_OF_STOCK);
        given(orderFacade.payOrder(MEMBER_ID, ORDER_ID, null)).willThrow(outOfStock);

        assertThatThrownBy(() -> service.payOrder(MEMBER_ID, ORDER_ID)).isSameAs(outOfStock);
        then(orderFacade).should().cancelByOutOfStock(ORDER_ID);
    }

    @Test
    @DisplayName("다른 이유로 결제가 실패하면 주문을 취소하지 않는다")
    void otherFailureDoesNotCancel() {
        ServiceException rejected = new ServiceException("400-INSUFFICIENT_BALANCE", "예치금이 부족합니다.");
        given(orderFacade.payOrder(MEMBER_ID, ORDER_ID, null)).willThrow(rejected);

        assertThatThrownBy(() -> service.payOrder(MEMBER_ID, ORDER_ID)).isSameAs(rejected);
        then(orderFacade).should(never()).cancelByOutOfStock(any());
    }
}
