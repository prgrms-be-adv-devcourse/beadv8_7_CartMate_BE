package com.cartmate.cartmate.boundedContext.order.out.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    boolean existsByOrderNumber(String orderNumber);
}
