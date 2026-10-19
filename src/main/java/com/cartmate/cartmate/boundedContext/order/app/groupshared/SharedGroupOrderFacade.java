package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SharedGroupOrderFacade {

    private final SharedGroupOrderCreateUseCase sharedGroupOrderCreateUseCase;
    private final SharedGroupOrderQueryUseCase sharedGroupOrderQueryUseCase;
    private final SharedGroupOrderPaymentUseCase sharedGroupOrderPaymentUseCase;
    private final SharedGroupOrderCancelUseCase sharedGroupOrderCancelUseCase;
    private final SharedGroupOrderRefundUseCase sharedGroupOrderRefundUseCase;
}
