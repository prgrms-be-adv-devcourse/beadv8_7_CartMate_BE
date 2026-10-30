package com.cartmate.cartmate.global.security;

public record SecurityUser(
        Long memberId,
        String email,
        String role,
        Long sellerId   // 판매자면 판매자 ID, 구매자·관리자면 null
) {

    // 판매자 정보가 필요 없는 곳(기존 테스트 등)은 그대로 쓸 수 있게
    public SecurityUser(Long memberId, String email, String role) {
        this(memberId, email, role, null);
    }
}