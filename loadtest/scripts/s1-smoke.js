// S1 연기 테스트: 1명이 30초 동안 결제 → 연결 · 헤더 · Cloud 수집 확인
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { charge, orderIdOf } from './lib/payment.js';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    checks: ['rate==1'],
    server_errors_5xx: ['count==0'],
  },
};

export default function () {
  const n = exec.scenario.iterationInTest;
  const res = charge(orderIdOf(n), 10001 + (n % 1000), 10000);
  check(res, { '결제 200-1': (r) => r.status === 200 && r.json('resultCode') === '200-1' });
  sleep(0.5);
}
