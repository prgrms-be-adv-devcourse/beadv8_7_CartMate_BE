package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByIdempotencyKey(String idempotencyKey);
}
