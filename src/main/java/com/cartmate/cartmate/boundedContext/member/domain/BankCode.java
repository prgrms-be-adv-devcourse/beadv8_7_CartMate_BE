package com.cartmate.cartmate.boundedContext.member.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

// 판매자 폼의 은행 이름 → 오픈뱅킹 표준 은행 코드. 코드 값은 오픈뱅킹 가이드의 코드표로 확인한다.
@Getter
@RequiredArgsConstructor
public enum BankCode {
    SHINHAN("신한은행", "088"),
    KOOKMIN("국민은행", "004"),
    WOORI("우리은행", "020"),
    HANA("하나은행", "081"),
    NONGHYUP("농협은행", "011"),
    IBK("기업은행", "003"),
    KAKAO("카카오뱅크", "090"),
    TOSS("토스뱅크", "092");

    private final String displayName;
    private final String code;

    public static BankCode fromName(String name) {
        return Arrays.stream(values())
                .filter(bank -> bank.displayName.equals(name))
                .findFirst()
                .orElseThrow(() -> new ServiceException("400-SELLER_BANK_NOT_SUPPORTED", "지원하지 않는 은행입니다."));
    }
}
