package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.shared.order.IndividualGroupOrderQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndividualGroupOrderQueryFacade implements IndividualGroupOrderQuery {

    private final OrderQueryUseCase orderQueryUseCase;

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrder(Long individualGroupPurchaseId, Long productId) {
        return orderQueryUseCase.hasActiveIndividualGroupOrder(individualGroupPurchaseId, productId);
    }
}
