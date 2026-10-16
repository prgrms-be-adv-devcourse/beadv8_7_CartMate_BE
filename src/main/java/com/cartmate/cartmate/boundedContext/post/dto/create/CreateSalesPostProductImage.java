package com.cartmate.cartmate.boundedContext.post.dto.create;

import com.cartmate.cartmate.shared.product.dto.create.CreateProductImage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesPostProductImage {
    private Long id;
    private Long productId;
    private String s3Key;
    private int image_seq;
    private MultipartFile file;

    public CreateProductImage toCreateProductImage(Long sellerId) {
        return new CreateProductImage(
                id,
                sellerId,
                productId,
                s3Key,
                image_seq,
                file
        );
    }
}
