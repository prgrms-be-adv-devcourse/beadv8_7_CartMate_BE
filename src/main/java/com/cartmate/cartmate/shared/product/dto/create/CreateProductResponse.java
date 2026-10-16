package com.cartmate.cartmate.shared.product.dto.create;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductResponse {
    private Long postId;
    private List<CreateProduct> productIdList;
}
