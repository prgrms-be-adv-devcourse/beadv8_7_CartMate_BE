package com.cartmate.cartmate.boundedContext.order.in.personal;

import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.order.app.personal.IndividualGroupOrderQueryFacade;
import com.cartmate.cartmate.boundedContext.order.out.personal.IndividualGroupPurchaseOrderLineView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IndividualGroupPurchaseOrderClientAdapter implements IndividualGroupPurchaseOrderClient {

    private final IndividualGroupOrderQueryFacade individualGroupOrderQueryFacade;

    @Override
    public boolean hasOrders(Long individualGroupPurchaseId, Long productId) {
        return individualGroupOrderQueryFacade.hasActiveIndividualGroupOrder(individualGroupPurchaseId, productId);
    }

    @Override
    public List<IndividualGroupPurchaseOrderLine> findOrderLines(Long individualGroupPurchaseId) {
        return individualGroupOrderQueryFacade.findPaidIndividualGroupOrderLines(individualGroupPurchaseId).stream()
                .map(this::toOrderLine)
                .toList();
    }

    private IndividualGroupPurchaseOrderLine toOrderLine(IndividualGroupPurchaseOrderLineView view) {
        return new IndividualGroupPurchaseOrderLine(
                view.orderId(),
                view.buyerMemberId(),
                view.productId(),
                view.inventoryId(),
                view.quantity(),
                view.unitPrice(),
                view.orderedAt()
        );
    }
}
