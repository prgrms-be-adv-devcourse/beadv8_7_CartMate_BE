package com.cartmate.cartmate.boundedContext.support.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 공지 구분. 사용자 화면의 탭과 배지에 쓴다.
@Getter
@RequiredArgsConstructor
public enum NoticeCategory {
    NOTICE("공지"),
    GUIDE("안내"),
    EVENT("이벤트"),
    URGENT("긴급");

    // 화면·로그에 쓰는 설명. 새 값을 추가할 때 설명이 없으면 컴파일되지 않는다.
    private final String description;
}