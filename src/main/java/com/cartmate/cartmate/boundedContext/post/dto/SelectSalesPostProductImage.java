package com.cartmate.cartmate.boundedContext.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SelectSalesPostProductImage {
    private Long id;
    private String s3key;
    private int image_seq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
