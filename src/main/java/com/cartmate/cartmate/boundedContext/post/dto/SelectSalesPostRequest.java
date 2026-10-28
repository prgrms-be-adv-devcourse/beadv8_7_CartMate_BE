package com.cartmate.cartmate.boundedContext.post.dto;

import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SelectSalesPostRequest {
    private Long id;
    private String title;
    private String content;
    private String productName;
}
