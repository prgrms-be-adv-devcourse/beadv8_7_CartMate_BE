package com.cartmate.cartmate.shared.post.event;

// 판매글이 더 이상 팔리지 않게 된 이유.
// 지금은 셋 다 똑같이 진행 중 거래를 취소·환불한다.
// 나중에 유형·사유별로 "관리자 승인 후 처리"를 붙일 때 이 값으로 나눈다.
public enum SalesPostHideType {
    SELLER_PAUSED,    // 판매자가 판매 중지
    ADMIN_SUSPENDED,  // 관리자 숨김
    SELLER_DELETED    // 판매자가 판매글 삭제
}
