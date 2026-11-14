package com.cartmate.cartmate.shared.post.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 판매글(또는 판매글의 상품 일부)이 더 이상 팔리지 않게 된 이유.
// 지금은 모두 똑같이 진행 중 거래를 취소·환불한다.
// 나중에 유형·사유별로 "관리자 승인 후 처리"를 붙일 때 이 값으로 나눈다.
@Getter
@RequiredArgsConstructor
public enum SalesPostHideType {
    SELLER_PAUSED("판매자 판매 중지", "판매자가 판매 중지"),
    ADMIN_SUSPENDED("관리자 숨김"),
    SELLER_DELETED("판매자 삭제", "판매자가 판매글 삭제"),
    // 판매글은 그대로 두고 상품 하나만 삭제. 이벤트의 productIds에는 삭제한 상품만 담긴다
    PRODUCT_DELETED("판매자 상품 삭제", "판매자가 판매글의 상품 하나를 삭제");

    private final String label;
    // 화면·로그에 쓰는 설명. 새 값을 추가할 때 설명이 없으면 컴파일되지 않는다.
    private final String description;

    // 라벨만으로 충분하면 라벨을 설명으로도 쓴다.
    SalesPostHideType(String label) {
        this(label, label);
    }
}