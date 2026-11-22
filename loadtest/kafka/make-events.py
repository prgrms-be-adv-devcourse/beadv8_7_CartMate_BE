"""주문 취소 이벤트(OrderCanceledEvent) 메시지 파일을 만든다 → kafka-console-producer로 넣는다.

형식은 주문 서비스 Outbox가 보내는 것과 같다: 값 = EventEnvelope JSON (payload는 이벤트 JSON 문자열), 키 = 주문 ID.
한 줄 = "키|값" (console-producer의 parse.key=true, key.separator=|).

주문 · 회원 · 금액은 kafka-prepare.js(k6)가 미리 만든 결제와 같은 규칙이다 (같은 RUN_ID를 넘긴다).

  python make-events.py --run-id 12345 --count 5000 --mode normal --out out/k2.txt
  mode: normal (주문마다 1개) / dup (같은 eventId 3번 + 다른 eventId 1번) / poison (정상 사이에 깨진 메시지 10개)
"""
import argparse
import json
import uuid
from datetime import datetime

TOPIC = "cartmate.order.order-canceled"
ORDER_BASE = 800000          # kafka-prepare.js 와 같아야 한다
MEMBER_BASE = 10001          # 회원 10001~11000 (reset-seed.sql S2 지갑)


def order_id(run_id, i):
    return run_id * 10000000 + ORDER_BASE + i


def member_id(i):
    return MEMBER_BASE + (i % 1000)


def envelope(oid, mid, event_id=None):
    now = datetime.now().isoformat(timespec="microseconds")
    payload = {
        "orderId": oid,
        "memberId": mid,
        "orderType": "INDIVIDUAL_GROUP",
        "cancelReason": "BUYER_CANCELED",
        "paid": True,
        "canceledAt": now,
        "refundAmount": None,       # 남은 금액 전부 환불
    }
    return {
        "eventId": event_id or str(uuid.uuid4()),
        "eventType": "OrderCanceledEvent",
        "topic": TOPIC,
        "aggregateType": "Order",
        "aggregateId": str(oid),
        "occurredAt": now,
        "version": 1,
        "payload": json.dumps(payload, separators=(",", ":")),
    }


def line(oid, value):
    return f"{oid}|{json.dumps(value, separators=(',', ':'), ensure_ascii=False)}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-id", type=int, required=True)
    ap.add_argument("--count", type=int, required=True)
    ap.add_argument("--mode", choices=["normal", "dup", "poison"], default="normal")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    lines = []
    for i in range(a.count):
        oid, mid = order_id(a.run_id, i), member_id(i)
        env = envelope(oid, mid)
        if a.mode == "dup":
            # Kafka 재전송 흉내(같은 eventId 3번) + 주문 서비스가 같은 취소를 다시 발행한 경우(다른 eventId)
            lines += [line(oid, env)] * 3
            lines.append(line(oid, envelope(oid, mid)))
        else:
            lines.append(line(oid, env))

    if a.mode == "poison":
        # 형식이 깨진 메시지 10개를 고르게 섞는다 → JSON 오류는 재시도 없이 바로 DLT
        step = max(1, len(lines) // 10)
        for k in range(10):
            lines.insert(k * step + k, f"poison-{k}|{{이것은 JSON이 아님 {k}")

    with open(a.out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{a.out}: 메시지 {len(lines)}개 (mode={a.mode}, 주문 {a.count}개)")


if __name__ == "__main__":
    main()
