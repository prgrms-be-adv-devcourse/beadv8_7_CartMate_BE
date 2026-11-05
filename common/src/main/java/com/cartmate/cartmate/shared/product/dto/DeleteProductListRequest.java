package com.cartmate.cartmate.shared.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DeleteProductListRequest {
    private List<SyncProduct> productList;
}
