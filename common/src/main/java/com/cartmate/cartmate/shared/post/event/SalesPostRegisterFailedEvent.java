package com.cartmate.cartmate.shared.post.event;

import com.cartmate.cartmate.global.event.DomainEvent;

/**
 * 판매글 등록이 실패했거나 등록 중(PENDING)으로 오래 멈춰 정리할 때 post가 발행한다.
 *
 * <p>product는 판매글에 속한 상품을 DB에서 지우고(hard delete) 사진 파일도 지운다.
 * 같은 이벤트가 두 번 와도 결과가 같아야 한다(멱등). Kafka 토픽: cartmate.post.sales-post-register-failed
 *
 * @param postId 등록에 실패한 판매글 ID
 */
public record SalesPostRegisterFailedEvent(
        Long postId
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SalesPost";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(postId);
    }
}
