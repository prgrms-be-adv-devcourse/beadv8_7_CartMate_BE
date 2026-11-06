package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import java.util.List;

public interface ProductLabelClient {

    List<String> labelProductTypes(List<String> productNames);
}
