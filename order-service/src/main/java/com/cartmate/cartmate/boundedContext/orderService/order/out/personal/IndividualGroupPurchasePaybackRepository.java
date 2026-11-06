package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchasePayback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndividualGroupPurchasePaybackRepository extends JpaRepository<IndividualGroupPurchasePayback, Long> {
}
