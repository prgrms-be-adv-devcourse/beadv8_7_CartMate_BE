package com.cartmate.cartmate.boundedContext.productService.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UploadedImage {
    private final int productIndex;
    private final int imageSeq;
    private final String s3Key;
}
