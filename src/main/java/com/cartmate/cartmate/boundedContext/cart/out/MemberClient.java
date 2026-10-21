package com.cartmate.cartmate.boundedContext.cart.out;

import java.util.Collection;
import java.util.Map;

public interface MemberClient {

    Map<Long, String> findNicknames(Collection<Long> memberIds);
}
