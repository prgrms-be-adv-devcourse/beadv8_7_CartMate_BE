package com.cartmate.cartmate.boundedContext.order.out;

import java.util.Collection;
import java.util.Map;

public interface OrderSharedGroupPurchaseClient {

    Map<Long, String> findTitles(Collection<Long> sharedGroupPurchaseIds);
}
