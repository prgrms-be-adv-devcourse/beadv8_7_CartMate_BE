package com.cartmate.cartmate.boundedContext.member.out.account;

import com.cartmate.cartmate.boundedContext.member.domain.BankCode;

import java.util.List;

public interface AccountVerifier {

    // 은행에 등록된 예금주명이 acceptableHolders 중 하나와 같으면 true. 실명번호가 틀리거나 계좌가 없어도 false.
    // 호출 자체가 실패하면 503 ServiceException을 던진다.
    boolean isHolderMatched(BankCode bank, String accountNumber, String realNumber, List<String> acceptableHolders);
}
