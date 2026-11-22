"""인박스 적체를 1초마다 재서 기록한다.

Kafka lag만 보면 안 되는 이유: 결제 서비스는 메시지를 받자마자 inbox_event 테이블에 저장(NEW)하고 Kafka 오프셋을 커밋한다.
실제 환불은 인박스 워커가 1초마다 N건씩 처리하므로, 이벤트가 몰리면 Kafka lag은 금방 0이 되고 인박스(NEW)에 쌓인다.

출력
  - logs/inbox-watch.log : JSON 한 줄씩 (event=loadtest.inbox) → Alloy가 Cloud Loki로 보내 대시보드 "인박스 적체" 패널이 그린다
  - 화면 : 경과 초, NEW, PROCESSED, FAILED, 환불 건수
NEW가 0이고 기대 환불 수(--expect)에 닿으면 끝난다.

  python watch-inbox.py --label K2 --expect 5000
"""
import argparse
import json
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

SQL = (
    "SELECT"
    " (SELECT COUNT(*) FROM inbox_event WHERE status='NEW'),"
    " (SELECT COUNT(*) FROM inbox_event WHERE status='PROCESSED'),"
    " (SELECT COUNT(*) FROM inbox_event WHERE status='FAILED'),"
    " (SELECT COUNT(*) FROM payment_refund WHERE reason='BUYER_CANCEL')"
)


def sample():
    out = subprocess.run(
        ["docker", "exec", "cartmate-lt-mysql", "mysql", "-uroot", "-ploadtest", "-N", "-B",
         "cartmate_payment", "-e", SQL],
        capture_output=True, text=True, check=True).stdout.strip().split()
    return [int(x) for x in out]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--expect", type=int, required=True, help="끝났다고 볼 환불 건수")
    ap.add_argument("--timeout", type=int, default=900)
    a = ap.parse_args()

    log = Path(__file__).resolve().parent.parent / "logs" / "inbox-watch.log"
    log.parent.mkdir(exist_ok=True)
    start = time.time()
    first_refund_at = None
    peak_new = 0
    print("초\tNEW\tPROCESSED\tFAILED\t환불")
    with open(log, "a", encoding="utf-8") as f:
        while True:
            new, processed, failed, refunds = sample()
            el = time.time() - start
            peak_new = max(peak_new, new)
            if refunds and first_refund_at is None:
                first_refund_at = el
            f.write(json.dumps({
                "@timestamp": datetime.now(timezone.utc).isoformat(), "level": "INFO",
                "event": "loadtest.inbox", "label": a.label,
                "new": new, "processed": processed, "failed": failed, "refunds": refunds,
            }) + "\n")
            f.flush()
            print(f"{el:5.0f}\t{new}\t{processed}\t{failed}\t{refunds}", flush=True)
            if refunds >= a.expect and new == 0:
                break
            if el > a.timeout:
                print("시간 초과")
                break
            time.sleep(1)
    total = time.time() - start
    print(f"[{a.label}] 최대 적체 {peak_new}건, 첫 환불 {first_refund_at or 0:.0f}초, "
          f"환불 {refunds}건 완료까지 {total:.0f}초 → 평균 {refunds / total:.1f}건/초")


if __name__ == "__main__":
    main()
