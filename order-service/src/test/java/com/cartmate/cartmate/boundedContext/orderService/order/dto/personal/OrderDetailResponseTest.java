package com.cartmate.cartmate.boundedContext.orderService.order.dto.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.ballLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.productBLine;
import static org.assertj.core.api.Assertions.assertThat;

class OrderDetailResponseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);

    @Test
    @DisplayName("판매글 내림으로 한 판매자 몫만 취소되면 그 항목에만 취소 시각이 보인다")
    void showsCanceledAtOfSalesStoppedItems() {
        Order order = personalOrder("20261109-TWOSELLR", 1L, List.of(ballLine(10L, 1), productBLine(3L, 1)), NOW);
        order.markPaid(1L, NOW);
        order.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(5));

        OrderDetailResponse response = OrderDetailResponse.from(order);

        assertThat(response.items())
                .extracting(OrderDetailResponse.ItemResponse::productName, OrderDetailResponse.ItemResponse::canceledAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("축구공", null),
                        org.assertj.core.groups.Tuple.tuple("상품B", NOW.plusMinutes(5)));
    }
}
