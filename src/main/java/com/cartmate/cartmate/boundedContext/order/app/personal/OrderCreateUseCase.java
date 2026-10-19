package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class OrderCreateUseCase {

    private final OrderRepository orderRepository;
    private final OrderProductClient orderProductClient;
    private final OrderNumberGenerator orderNumberGenerator;
    private final CartFacade cartFacade;

    OrderCreateResponse createPersonal(Long memberId, List<Long> cartItemIds, DeliveryAddress deliveryAddress,
                                       LocalDateTime now) {
        List<CartOrderItem> cartItems = cartFacade.getCartItemsForOrder(memberId, cartItemIds);
        Map<Long, OrderProductInfo> products = productsOf(cartItems.stream().map(CartOrderItem::inventoryId).toList());
        List<OrderLine> lines = cartItems.stream()
                .map(item -> toLine(products, item.inventoryId(), item.quantity()))
                .toList();
        Order order = Order.createPersonal(generateOrderNumber(now), memberId, lines, deliveryAddress, now);
        return OrderCreateResponse.from(orderRepository.save(order));
    }

    private String generateOrderNumber(LocalDateTime now) {
        return orderNumberGenerator.generateUnique(now.toLocalDate(), orderRepository::existsByOrderNumber);
    }

    private Map<Long, OrderProductInfo> productsOf(List<Long> inventoryIds) {
        return orderProductClient.findAllByInventoryIds(inventoryIds).stream()
                .collect(Collectors.toMap(OrderProductInfo::inventoryId, Function.identity()));
    }

    private OrderLine toLine(Map<Long, OrderProductInfo> products, Long inventoryId, int quantity) {
        OrderProductInfo product = products.get(inventoryId);
        if (product == null) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_FOUND);
        }
        if (!product.purchasable()) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE);
        }
        return new OrderLine(
                product.productId(),
                inventoryId,
                product.sellerId(),
                product.productName(),
                product.optionName(),
                product.price(),
                quantity
        );
    }
}
