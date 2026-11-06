package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderPurchaseConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPurchaseConfirmationRepository extends JpaRepository<OrderPurchaseConfirmation, Long> {
}
