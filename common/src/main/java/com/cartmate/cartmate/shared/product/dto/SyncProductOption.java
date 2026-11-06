package com.cartmate.cartmate.shared.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SyncProductOption {
    private Long id;
    private Long productTypeId;
    private Long productOptionId;
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;
}
