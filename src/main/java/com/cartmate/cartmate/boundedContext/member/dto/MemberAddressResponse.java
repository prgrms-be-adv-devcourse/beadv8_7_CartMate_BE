package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.MemberAddress;

public record MemberAddressResponse(
        Long id,
        String addressName,
        String receiverName,
        String receiverPhone,
        String zipCode,
        String basicAddress,
        String detailAddress,
        boolean defaultAddress
) {
    public static MemberAddressResponse from(MemberAddress a) {
        return new MemberAddressResponse(a.getId(), a.getAddressName(), a.getReceiverName(), a.getReceiverPhone(),
                a.getZipCode(), a.getBasicAddress(), a.getDetailAddress(), a.isDefaultAddress());
    }
}