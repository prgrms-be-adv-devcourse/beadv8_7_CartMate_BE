package com.cartmate.cartmate.boundedContext.support.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
// 고객센터 모듈 에러 코드 (공지, 이후 FAQ·문의도 여기에 추가)
public enum SupportErrorCode implements ErrorCode {

    NOTICE_NOT_FOUND("NOTICE_NOT_FOUND", "공지사항을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}