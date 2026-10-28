package com.cartmate.cartmate.shared.product.dto;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SyncProductRequest {
    private List<SyncProduct> productList;
}
