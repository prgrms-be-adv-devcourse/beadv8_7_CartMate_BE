package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class OrderQueryUseCase {

    private final OrderRepository orderRepository;

    List<OrderSummaryResponse> getMyList(Long memberId) {
        return orderRepository.findAllByMember(memberId).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    OrderDetailResponse getDetail(Long memberId, Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.isBuyer(memberId)) {
            throw new ServiceException(OrderErrorCode.ORDER_BUYER_ONLY);
        }
        return OrderDetailResponse.from(order);
    }
}
