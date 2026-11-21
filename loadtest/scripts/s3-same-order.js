// S3 같은 주문 동시 결제: 100명이 같은 주문(같은 키)으로 동시에 결제 — 재시도 · 응답 유실 · 따닥 흉내
// 라운드 20번, 라운드마다 다른 주문 · 다른 회원(15001~15020)
// 기대: 주문마다 결제 1건 · 차감 1번 (verify.sql V1 · V3 · V4). 응답은 200-1(성공 또는 DUPLICATE) 또는 409(처리 중). 5xx 0건
import { check } from 'k6';
import exec from 'k6/execution';
import { charge, orderIdOf, waitUntil } from './lib/payment.js';

const ROUNDS = Number(__ENV.ROUNDS || 20);
const GAP_MS = 1500;   // 라운드 간격 (그 안에 100건이 다 끝나야 다음 출발선에서 다시 모인다)

export const options = {
  scenarios: {
    same_order: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 100),
      iterations: ROUNDS,
      maxDuration: '5m',
    },
  },
  thresholds: {
    server_errors_5xx: ['count==0'],
    checks: ['rate==1'],
  },
};

export function setup() {
  return { startAt: Date.now() + 3000 };   // 모든 가상 사용자가 준비될 시간
}

export default function (data) {
  const round = exec.vu.iterationInScenario;
  waitUntil(data.startAt + round * GAP_MS);
  const res = charge(orderIdOf(500000 + round), 15001 + round, 10000);
  check(res, { '성공·중복(200) 또는 처리 중(409)만': (r) => r.status === 200 || r.status === 409 });
}
