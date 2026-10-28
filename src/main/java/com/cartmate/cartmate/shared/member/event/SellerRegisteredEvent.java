package com.cartmate.cartmate.shared.member.event;

import com.cartmate.cartmate.boundedContext.member.domain.SellerStatus;

import java.time.LocalDateTime;

// 판매자 등록 시 발행. product, payout이 판매자 정보를 복제할 때 사용
public record SellerRegisteredEvent(
        Long sellerId,
        Long memberId,
        String sellerType,
        String displayName, // 사업자면 상호명, 개인이면 닉네임
        String businessNumber,
        String phoneNumber,
        String bankName,
        String accountHolder,
        String accountNumber,
        String status,
        LocalDateTime createDate,
        LocalDateTime modifyDate
) {

}