package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import java.util.List;

public record AdminBulkStatusResponse(
        int successCount,
        List<Failure> failures
) {
    public record Failure(Long memberId, String reason) {
    }
}