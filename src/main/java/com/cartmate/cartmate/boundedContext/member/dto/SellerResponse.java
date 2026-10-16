package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.Seller;

import java.time.LocalDateTime;

public record SellerResponse(
        Long id,
        String sellerType,
        String displayName,
        String businessName,
        String representativeName,
        String businessNumber,
        String phoneNumber,
        String bankName,
        String accountHolder,
        String maskedAccountNumber,
        String status,
        LocalDateTime registeredAt
) {
    public static SellerResponse from(Seller s) {
        return new SellerResponse(s.getId(), s.getSellerType().name(), s.getDisplayName(), s.getBusinessName(),
                s.getRepresentativeName(), s.getBusinessNumber(), s.getPhoneNumber(), s.getBankName(),
                s.getAccountHolder(), s.getMaskedAccountNumber(), s.getStatus().name(), s.getCreateDate());
    }
}