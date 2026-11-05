package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderItem;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderMemberClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderSharedGroupPurchaseClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class SharedGroupOrderQueryUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final OrderProductClient orderProductClient;
    private final OrderMemberClient orderMemberClient;
    private final OrderSharedGroupPurchaseClient orderSharedGroupPurchaseClient;

    List<SharedGroupOrderSummaryResponse> getMyList(Long memberId) {
        List<SharedGroupOrder> orders = sharedGroupOrderRepository.findAllByParticipant(memberId);
        Map<Long, String> titles = orderSharedGroupPurchaseClient.findTitles(orders.stream()
                .map(SharedGroupOrder::getSharedGroupPurchaseId)
                .collect(Collectors.toSet()));
        return orders.stream()
                .map(order -> SharedGroupOrderSummaryResponse.of(order, titles.get(order.getSharedGroupPurchaseId()), memberId))
                .toList();
    }

    SharedGroupOrderDetailResponse getDetail(Long memberId, Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderRepository.findWithDetailsById(sharedGroupOrderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.isParticipant(memberId)) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PARTICIPANT);
        }
        return SharedGroupOrderDetailResponse.of(order, productsOf(order), nicknamesOf(order), memberId, LocalDateTime.now());
    }

    private Map<Long, String> nicknamesOf(SharedGroupOrder order) {
        return orderMemberClient.findNicknames(order.getParticipants().stream()
                .map(SharedGroupOrderParticipant::getMemberId)
                .collect(Collectors.toSet()));
    }

    private Map<Long, OrderProductInfo> productsOf(SharedGroupOrder order) {
        List<Long> inventoryIds = order.getItems().stream()
                .map(SharedGroupOrderItem::getInventoryId)
                .toList();
        return orderProductClient.findAllByInventoryIds(inventoryIds).stream()
                .collect(Collectors.toMap(OrderProductInfo::inventoryId, Function.identity()));
    }

    List<Long> findIdsOfParticipant(Long memberId, Collection<SharedGroupOrderStatus> statuses) {
        return sharedGroupOrderRepository.findIdsByParticipantAndStatusIn(memberId, statuses);
    }
}
