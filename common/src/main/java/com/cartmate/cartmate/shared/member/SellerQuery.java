package com.cartmate.cartmate.shared.member;

import java.util.Optional;

public interface SellerQuery {

    // 회원에 연결된 판매자 ID를 조회한다. 판매자가 아니면 빈 Optional을 반환한다.
    Optional<Long> findSellerIdByMemberId(Long memberId);

    // 해당 회원이 지정한 판매자인지 확인한다.
    default boolean isSellerOwner(Long memberId, Long sellerId) {
        if (memberId == null || sellerId == null) {
            return false;
        }
        return findSellerIdByMemberId(memberId)
                .map(sellerId::equals)
                .orElse(false);
    }
}
