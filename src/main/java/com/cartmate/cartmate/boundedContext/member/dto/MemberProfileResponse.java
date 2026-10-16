package com.cartmate.cartmate.boundedContext.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemberProfileResponse(
        String email,
        String nickname,
        String phone,
        LocalDate birthDate,
        String role,
        LocalDateTime updatedAt,
        Address defaultAddress
) {
    public record Address(String zipCode, String basicAddress, String detailAddress) {
    }
}