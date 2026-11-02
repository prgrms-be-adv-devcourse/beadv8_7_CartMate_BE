package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.shared.cart.CartOrderQuery;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// cart 모듈 호출은 모두 shared 조회 창구를 거친다. 물리 분리 시 이 클래스만 HTTP 클라이언트로 바꾼다
@Component
@RequiredArgsConstructor
class CartQueryOrderClient implements OrderCartClient, OrderSharedGroupPurchaseClient {

    private final CartOrderQuery cartOrderQuery;

    @Override
    public List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds) {
        return cartOrderQuery.getCartItemsForOrder(memberId, cartItemIds);
    }

    @Override
    public IndividualGroupPurchaseOrderable getIndividualGroupPurchaseOrderable(Long individualGroupPurchaseId,
                                                                               List<Long> productIds, LocalDateTime now) {
        return cartOrderQuery.getIndividualGroupPurchaseOrderable(individualGroupPurchaseId, productIds, now);
    }

    @Override
    public Map<Long, String> findTitles(Collection<Long> sharedGroupPurchaseIds) {
        return cartOrderQuery.findSharedGroupPurchaseTitles(sharedGroupPurchaseIds);
    }
}
