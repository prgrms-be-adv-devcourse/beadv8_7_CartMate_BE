package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryFacade {

    private final DeliveryCreateUseCase deliveryCreateUseCase;

    @Transactional
    public void createDelivery(DeliveryCreateCommand createCommand) {
        deliveryCreateUseCase.create(createCommand);
    }
}
