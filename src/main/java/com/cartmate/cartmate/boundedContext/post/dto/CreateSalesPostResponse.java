package com.cartmate.cartmate.boundedContext.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesPostResponse {
    private Long postId;
    private List<Long> productIdList;
}
