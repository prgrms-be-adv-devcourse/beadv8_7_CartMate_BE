package com.cartmate.cartmate.boundedContext.etcService.delivery.out;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryItemRepository extends JpaRepository<DeliveryItem, Long> {

    List<DeliveryItem> findByDeliveryId(Long deliveryId);

    List<DeliveryItem> findByDeliveryIdIn(List<Long> deliveryIds);
}
