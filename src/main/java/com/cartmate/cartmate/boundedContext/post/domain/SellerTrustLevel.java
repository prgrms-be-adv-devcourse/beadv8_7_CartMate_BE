package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

// 판매글에 보여줄 판매자 신뢰도 등급. trust의 등급과 같은 이름을 쓰지만 post가 자체로 정의해, trust 도메인 코드에 의존하지 않는다.
public enum SellerTrustLevel {
    NEW,     //거래 표본 부족
    LOW,     //낮음
    NORMAL,  //보통
    HIGH;    //높음

    // 신뢰도 변경 이벤트의 등급 문자열을 등급으로 바꾼다.
    // 모르는 값이거나 null이면 SELLER_TRUST_INVALID 예외가 발생한다. 등급은 임의로 추측해 보여주지 않는다.
    public static SellerTrustLevel from(String level) {
        if (level == null) {
            throw new ServiceException(SalesPostErrorCode.SELLER_TRUST_INVALID);
        }

        try {
            return valueOf(level);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(SalesPostErrorCode.SELLER_TRUST_INVALID);
        }
    }
}
