package com.cartmate.cartmate.boundedContext.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class ImageUploadTask {
    private final int productIndex;
    private final int imageSeq;
    private final MultipartFile file;
}