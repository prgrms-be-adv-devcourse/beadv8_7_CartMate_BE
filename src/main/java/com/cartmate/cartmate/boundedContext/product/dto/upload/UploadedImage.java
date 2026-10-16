package com.cartmate.cartmate.boundedContext.product.dto.upload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UploadedImage {
    private final int productIndex;
    private final int imageSeq;
    private final String s3Key;
}
