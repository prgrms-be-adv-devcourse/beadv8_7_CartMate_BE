package com.cartmate.cartmate.boundedContext.post.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 관리자 판매글 제재 이력의 처리 종류
@Getter
@RequiredArgsConstructor
public enum SalesPostSanctionAction {
    SUSPEND("숨김"),
    RESUME("숨김 해제");

    // 화면·로그에 쓰는 설명. 새 값을 추가할 때 설명이 없으면 컴파일되지 않는다.
    private final String description;
}