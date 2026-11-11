package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.TrustedDevice;

import java.time.LocalDateTime;

public record TrustedDeviceResponse(Long id, String deviceName, LocalDateTime registeredAt,
                                    LocalDateTime lastUsedAt, LocalDateTime expiresAt) {

    public static TrustedDeviceResponse from(TrustedDevice device) {
        return new TrustedDeviceResponse(device.getId(), device.getDeviceName(), device.getCreateDate(),
                device.getLastUsedAt(), device.getExpiresAt());
    }
}
