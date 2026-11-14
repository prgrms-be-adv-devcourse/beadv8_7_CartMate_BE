package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.ballLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderQueryUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER = 1L;
    private static final Long OTHER = 2L;

    @Mock
    private OrderRepository orderRepository;

    private OrderQueryUseCase queryUseCase;

    @BeforeEach
    void setUp() {
        queryUseCase = new OrderQueryUseCase(orderRepository, new OrderSupport(orderRepository));
    }

    @Test
    @DisplayName("주문한 회원이 아니면 주문 상세를 볼 수 없다")
    void detailByNonBuyer() {
        Order order = personalOrder(BUYER, ballLine(null, 1), LocalDateTime.now());
        given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> queryUseCase.getDetail(OTHER, ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_BUYER_ONLY.getMsg());
    }
}
