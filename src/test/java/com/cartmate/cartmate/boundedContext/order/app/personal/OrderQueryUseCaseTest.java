package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderQueryUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER = 1L;
    private static final Long OTHER = 2L;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderQueryUseCase queryUseCase;

    @Test
    @DisplayName("주문한 회원이 아니면 주문 상세를 볼 수 없다")
    void detailByNonBuyer() {
        Order order = Order.createPersonal("20261019-ABCDEFGH", BUYER,
                List.of(new OrderLine(2L, 4L, null, "축구공", "5호", 25000, 1)),
                DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", null), LocalDateTime.now());
        given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> queryUseCase.getDetail(OTHER, ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_BUYER_ONLY.getMsg());
    }
}
