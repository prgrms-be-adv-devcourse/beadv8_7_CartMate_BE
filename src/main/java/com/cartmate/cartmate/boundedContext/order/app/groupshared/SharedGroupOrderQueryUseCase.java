package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderItem;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class SharedGroupOrderQueryUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final OrderProductClient orderProductClient;

    List<SharedGroupOrderSummaryResponse> getMyList(Long memberId) {
        return sharedGroupOrderRepository.findAllByParticipant(memberId).stream()
                .map(order -> SharedGroupOrderSummaryResponse.of(order, memberId))
                .toList();
    }

    SharedGroupOrderDetailResponse getDetail(Long memberId, Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderRepository.findWithDetailsById(sharedGroupOrderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.isParticipant(memberId)) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PARTICIPANT);
        }
        return SharedGroupOrderDetailResponse.of(order, productsOf(order), memberId, LocalDateTime.now());
    }

    private Map<Long, OrderProductInfo> productsOf(SharedGroupOrder order) {
        List<Long> inventoryIds = order.getItems().stream()
                .map(SharedGroupOrderItem::getInventoryId)
                .toList();
        return orderProductClient.findAllByInventoryIds(inventoryIds).stream()
                .collect(Collectors.toMap(OrderProductInfo::inventoryId, Function.identity()));
    }
}
