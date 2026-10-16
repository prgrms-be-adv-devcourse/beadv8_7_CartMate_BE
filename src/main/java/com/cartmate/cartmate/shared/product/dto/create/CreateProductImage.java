package com.cartmate.cartmate.shared.product.dto.create;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductImage {
    private Long id;
    private Long sellerId;
    private Long productId;
    private String s3Key;
    private int imageSeq;
    private MultipartFile file;
}
