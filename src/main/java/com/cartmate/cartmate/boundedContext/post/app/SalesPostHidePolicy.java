package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import org.springframework.stereotype.Component;

/**
 * 판매글이 내려갔을 때 진행 중 거래를 바로 정리할지 정한다.
 *
 * <p>지금은 유형·사유와 관계없이 항상 바로 정리한다.
 * 나중에 "사유에 따라 관리자 승인 후 처리"를 붙일 때 여기서 false를 돌려주고,
 * 승인 대기 목록에 넣은 뒤 승인 시점에 이벤트를 발행하면 된다.
 */
@Component
public class SalesPostHidePolicy {

    public boolean settleImmediately(SalesPostHideType hideType, String reason) {
        return true;
    }
}