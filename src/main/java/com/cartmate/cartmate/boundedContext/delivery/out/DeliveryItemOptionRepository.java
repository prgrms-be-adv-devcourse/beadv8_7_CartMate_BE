package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItemOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryItemOptionRepository extends JpaRepository<DeliveryItemOption, Long> {

    List<DeliveryItemOption> findByDeliveryItemIdInOrderByDeliveryItemIdAscSortOrderAsc(List<Long> deliveryItemIds);
}
