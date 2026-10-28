package com.cartmate.cartmate.boundedContext.post.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SalesPostSanctionRequest(
        @NotBlank(message = "사유를 입력해주세요.")
        @Size(max = 500, message = "사유는 500자 이하로 입력해주세요.")
        String reason
) {
}
