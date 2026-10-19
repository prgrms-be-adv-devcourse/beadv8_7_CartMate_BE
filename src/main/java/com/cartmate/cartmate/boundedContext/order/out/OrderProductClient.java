package com.cartmate.cartmate.boundedContext.order.out;

import java.util.Collection;
import java.util.List;

public interface OrderProductClient {

    List<OrderProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds);
}
