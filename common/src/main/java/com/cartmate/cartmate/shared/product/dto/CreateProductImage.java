package com.cartmate.cartmate.shared.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // 서버 도착 후 주입
    @JsonIgnore
    private MultipartFile file;

    public void assignFile(MultipartFile file) {
        this.file = file;
    }
}
