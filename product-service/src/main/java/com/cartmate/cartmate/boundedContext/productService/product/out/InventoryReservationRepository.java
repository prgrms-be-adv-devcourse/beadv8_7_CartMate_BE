package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.product.domain.InventoryReservation;
import com.cartmate.cartmate.shared.order.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderTypeAndOrderIdOrderByInventoryIdAsc(OrderType orderType, Long orderId);
}
