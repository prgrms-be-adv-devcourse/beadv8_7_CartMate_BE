package com.cartmate.cartmate.boundedContext.member.dto;

import java.time.Duration;

public record TokenPair(
        String accessToken,
        String refreshToken,
        boolean remember,
        Duration refreshTokenMaxAge
) {
}