package com.cartmate.cartmate.boundedContext.order.in.personal;

import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.order.out.personal.IndividualGroupPurchaseOrderLineView;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IndividualGroupPurchaseOrderClientAdapter implements IndividualGroupPurchaseOrderClient {

    private final OrderFacade orderFacade;

    public IndividualGroupPurchaseOrderClientAdapter(@Lazy OrderFacade orderFacade) {
        this.orderFacade = orderFacade;
    }

    @Override
    public boolean hasOrders(Long individualGroupPurchaseId, Long productId) {
        return orderFacade.hasActiveIndividualGroupOrder(individualGroupPurchaseId, productId);
    }

    @Override
    public List<IndividualGroupPurchaseOrderLine> findOrderLines(Long individualGroupPurchaseId) {
        return orderFacade.findPaidIndividualGroupOrderLines(individualGroupPurchaseId).stream()
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
