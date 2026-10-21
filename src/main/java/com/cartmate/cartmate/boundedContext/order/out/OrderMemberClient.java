package com.cartmate.cartmate.boundedContext.order.out;

import java.util.Collection;
import java.util.Map;

public interface OrderMemberClient {

    Map<Long, String> findNicknames(Collection<Long> memberIds);
}
