package com.cartmate.cartmate.boundedContext.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSalesPostRequest {
    private Long id;
    private Long sellerId;
    private String title;
    private String content;
    private List<UpdateSalesPostProduct> productList;
}
