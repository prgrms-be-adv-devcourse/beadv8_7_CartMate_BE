package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// 테스트용 재고 선점: 상품 서버를 부르지 않고 호출만 기록한다. failReserveWith로 선점 실패를 흉내 낸다
@Component
@Primary
@Profile("test")
public class FakeOrderInventoryClient implements OrderInventoryClient {

    private final List<Long> reservedOrderIds = new CopyOnWriteArrayList<>();
    private final List<Long> releasedOrderIds = new CopyOnWriteArrayList<>();
    private volatile RuntimeException reserveFailure;

    public void failReserveWith(RuntimeException failure) {
        this.reserveFailure = failure;
    }

    public void reset() {
        reservedOrderIds.clear();
        releasedOrderIds.clear();
        reserveFailure = null;
    }

    public List<Long> reservedOrderIds() {
        return List.copyOf(reservedOrderIds);
    }

    public List<Long> releasedOrderIds() {
        return List.copyOf(releasedOrderIds);
    }

    @Override
    public void reserve(OrderType orderType, Long orderId, List<InventoryReserveRequest.Item> items) {
        RuntimeException failure = reserveFailure;
        if (failure != null) {
            throw failure;
        }
        reservedOrderIds.add(orderId);
    }

    @Override
    public void release(OrderType orderType, Long orderId) {
        releasedOrderIds.add(orderId);
    }
}
