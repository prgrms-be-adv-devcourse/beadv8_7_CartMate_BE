package com.cartmate.cartmate.boundedContext.productService.post.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SyncProductImage {
    private Long id;
    private Long sellerId;
    private String s3Key;
    private int imageSeq;
    private SalesPostProductStatus status;
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;
}
