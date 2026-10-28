package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.out.personal.IndividualGroupPurchaseOrderLineView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IndividualGroupOrderQueryFacade {

    private final OrderQueryUseCase orderQueryUseCase;

    @Transactional(readOnly = true)
    public boolean hasActiveIndividualGroupOrder(Long individualGroupPurchaseId, Long productId) {
        return orderQueryUseCase.hasActiveIndividualGroupOrder(individualGroupPurchaseId, productId);
    }

    @Transactional(readOnly = true)
    public List<IndividualGroupPurchaseOrderLineView> findPaidIndividualGroupOrderLines(Long individualGroupPurchaseId) {
        return orderQueryUseCase.findPaidIndividualGroupOrderLines(individualGroupPurchaseId);
    }
}
