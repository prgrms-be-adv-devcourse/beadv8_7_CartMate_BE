package com.cartmate.cartmate.boundedContext.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSalesPostProductImage {
    private Long id;
    private Long productId;
    private String s3Key;
    private int imageSeq;
}
