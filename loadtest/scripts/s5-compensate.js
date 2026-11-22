// S5 되돌리기 중복: 결제 10건을 만든 뒤, 결제마다 /compensate 를 100명이 동시에 호출
// 기대: 결제마다 환불 1건 · 전액 환불 · 지갑 잔액 원래대로 (verify.sql V3 · V7 · V8 · V9). 응답은 200 또는 409(처리 중). 5xx 0건
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { charge, compensate, orderIdOf, waitUntil } from './lib/payment.js';

const PAYMENTS = 10;
const GAP_MS = 1500;

export const options = {
  scenarios: {
    compensate: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 100),
      iterations: PAYMENTS,
      maxDuration: '5m',
    },
  },
  thresholds: {
    server_errors_5xx: ['count==0'],
    checks: ['rate==1'],
  },
};

const target = (k) => ({ orderId: orderIdOf(700000 + k), memberId: 30001 + k });

export function setup() {
  for (let k = 0; k < PAYMENTS; k++) {
    const { orderId, memberId } = target(k);
    const res = charge(orderId, memberId, 50000);
    if (res.status !== 200) {
      fail(`준비 결제 실패: 주문 ${orderId} → ${res.status} ${res.body}`);
    }
  }
  return { startAt: Date.now() + 3000 };
}

export default function (data) {
  const k = exec.vu.iterationInScenario;
  waitUntil(data.startAt + k * GAP_MS);
  const { orderId, memberId } = target(k);
  const res = compensate(orderId, memberId);
  check(res, { '되돌림(200) 또는 처리 중(409)만': (r) => r.status === 200 || r.status === 409 });
}
