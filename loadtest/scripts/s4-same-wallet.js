// S4 같은 지갑 경합: 한 회원의 서로 다른 주문 100건을 동시에 결제. 잔액 10만 원, 건당 5천 원 → 최대 20건만 가능
// 라운드 5번, 라운드마다 다른 회원(20001~20005)
// 기대: 잔액 음수 0 · 차감 합계 = 잔액 변화 (verify.sql V2 · V3 · V10). 잔액 부족은 409. 5xx 0건
//       라운드마다 성공 20건이면 "잔액만큼 정확히", 20건 미만이면 동시 충돌로 결제 가능한 요청까지 거절된 것 (RESULTS.md에 기록)
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { charge, orderIdOf, waitUntil } from './lib/payment.js';

const ROUNDS = Number(__ENV.ROUNDS || 5);
const GAP_MS = 2000;
const paid = new Counter('s4_paid');

export const options = {
  scenarios: {
    same_wallet: {
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
  return { startAt: Date.now() + 3000 };
}

export default function (data) {
  const round = exec.vu.iterationInScenario;
  waitUntil(data.startAt + round * GAP_MS);
  const res = charge(orderIdOf(600000 + round * 1000 + exec.vu.idInTest), 20001 + round, 5000);
  if (res.status === 200) {
    paid.add(1, { round: String(round) });
  }
  check(res, { '성공(200) 또는 잔액 부족(409)만': (r) => r.status === 200 || r.status === 409 });
}
