package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderPurchaseConfirmationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class OrderPurchaseConfirmationUseCase {

    private final OrderPurchaseConfirmationRepository confirmationRepository;

    void record(Long orderId, LocalDateTime confirmedAt) {
        confirmationRepository.insertIfAbsent(orderId, confirmedAt);
    }
}
