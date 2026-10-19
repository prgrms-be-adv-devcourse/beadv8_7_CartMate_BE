package com.cartmate.cartmate.shared.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeleteProductRequest {
    private Long productId;
}
