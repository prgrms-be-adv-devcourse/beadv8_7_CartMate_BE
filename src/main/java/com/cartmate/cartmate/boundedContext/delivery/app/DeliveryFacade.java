package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerDeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryFacade {

    private final DeliveryCreateUseCase deliveryCreateUseCase;
    private final DeliveryQueryUseCase deliveryQueryUseCase;

    @Transactional
    public void createDelivery(DeliveryCreateCommand createCommand) {
        deliveryCreateUseCase.create(createCommand);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> getMyDeliveries(Long memberId, Pageable pageable) {
        return deliveryQueryUseCase.getMyDeliveries(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SellerDeliveryResponse> getSellerDeliveries(Long memberId, DeliveryStatus status, Pageable pageable) {
        return deliveryQueryUseCase.getSellerDeliveries(memberId, status, pageable);
    }

    @Transactional(readOnly = true)
    public DeliveryDetailResponse getDeliveryDetails(Long deliveryId, Long memberId) {
        return deliveryQueryUseCase.getDeliveryDetails(deliveryId, memberId);
    }
}
