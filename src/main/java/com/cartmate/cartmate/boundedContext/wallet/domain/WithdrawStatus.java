package com.cartmate.cartmate.boundedContext.wallet.domain;

public enum WithdrawStatus {
    PENDING,    //출금대기
    COMPLETED,  //출금완료
    CANCELED,   //출금취소
    FAILED      //출금실패
}
