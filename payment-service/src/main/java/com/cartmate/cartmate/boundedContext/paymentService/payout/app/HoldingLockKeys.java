package com.cartmate.cartmate.boundedContext.paymentService.payout.app;

// 정산 지급이 잡는 주문 키 분산락 이름을 한곳에서 만든다.
// 한 주문의 홀딩 전체를 나눠 꺼내므로 키는 주문 단위다. 개인 주문과 공동물품 주문은 주문 ID가 겹칠 수 있어 구매 유형을 함께 넣는다.
// 환불은 이 락을 잡지 않는다. Inbox 트랜잭션 안에서 실행돼 락이 커밋 전에 풀리므로 버전으로 막는다.
public final class HoldingLockKeys {

    private HoldingLockKeys() {
    }

    public static String of(String purchaseType, Long orderId) {
        return "holding-order:" + purchaseType + ":" + orderId;
    }
}
