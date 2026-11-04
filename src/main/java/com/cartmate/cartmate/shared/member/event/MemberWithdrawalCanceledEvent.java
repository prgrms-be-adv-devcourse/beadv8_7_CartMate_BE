package com.cartmate.cartmate.shared.member.event;

// 회원이 탈퇴 대기 중에 탈퇴를 취소했다 (판매 중지한 판매글은 판매자가 직접 재개한다)
public record MemberWithdrawalCanceledEvent(Long memberId) {
}