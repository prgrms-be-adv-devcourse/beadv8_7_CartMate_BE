package com.cartmate.cartmate.global.security;

public record SecurityUser(
        Long memberId,
        String email,
        String role
) {
}