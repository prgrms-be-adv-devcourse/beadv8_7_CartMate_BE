package com.cartmate.cartmate.shared.post.event;

import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostDeletedEvent {
    private final DeleteProductRequest request;
}
