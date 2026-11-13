package com.cartmate.cartmate.support;

/**
 * data.sql 초기 데이터의 옵션 종류 · 상품옵션 ID. 테스트는 이 값으로 선택된 옵션을 만든다.
 * data.sql의 ID를 바꾸면 여기도 같이 바꾼다.
 */
public final class SeedOptions {

    // 옵션 종류
    public static final long CLOTHING_SIZE = 1L;
    public static final long COLOR = 2L;
    public static final long SHOE_SIZE = 3L;

    // 상품옵션
    public static final long SIZE_M = 3L;       // 의류 사이즈 M
    public static final long SIZE_L = 4L;       // 의류 사이즈 L
    public static final long BLACK = 8L;        // 색상 블랙
    public static final long WHITE = 9L;        // 색상 화이트
    public static final long SHOE_220 = 18L;    // 신발 사이즈 220
    public static final long SHOE_255 = 25L;    // 신발 사이즈 255
    public static final long SHOE_265 = 27L;    // 신발 사이즈 265

    private SeedOptions() {
    }
}
