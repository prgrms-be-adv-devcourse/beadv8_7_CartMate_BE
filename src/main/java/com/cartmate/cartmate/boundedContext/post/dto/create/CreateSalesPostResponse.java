package com.cartmate.cartmate.boundedContext.post.dto.create;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesPostResponse {
    private int postId;
    private List<Integer> productIdList;
}
