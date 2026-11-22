// Kafka 환불 테스트 준비: 취소할 결제를 미리 만든다 (주문 ID · 회원 · 금액 규칙은 kafka/make-events.py와 같다)
//   -e RUN_ID=12345 -e COUNT=5000   (RUN_ID는 make-events.py --run-id와 같은 값)
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { charge, orderIdOf } from './lib/payment.js';

const COUNT = Number(__ENV.COUNT || 10);
const ORDER_BASE = 800000;

export const options = {
  scenarios: {
    prepare: {
      executor: 'shared-iterations',
      vus: Math.min(COUNT, 20),
      iterations: COUNT,
      maxDuration: '10m',
    },
  },
  thresholds: { checks: ['rate==1'] },
};

export function setup() {
  if (!__ENV.RUN_ID) {
    fail('RUN_ID를 넘기세요 (make-events.py --run-id와 같은 값)');
  }
}

export default function () {
  const i = exec.scenario.iterationInTest;
  const res = charge(orderIdOf(ORDER_BASE + i), 10001 + (i % 1000), 10000);
  check(res, { '결제 200': (r) => r.status === 200 });
}
