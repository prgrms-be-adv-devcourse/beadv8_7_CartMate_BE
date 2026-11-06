package com.cartmate.cartmate.shared.post.event;

import com.cartmate.cartmate.shared.post.dto.RollbackProductRequest;

public record PostApiCallFailedEvent(RollbackProductRequest rollbackProductRequest) {
}
