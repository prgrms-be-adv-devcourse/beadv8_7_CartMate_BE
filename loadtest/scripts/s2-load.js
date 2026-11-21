// S2 정상 부하: 초당 요청 수를 10 → 30 → 50 → 100으로 2분씩 (주문마다 다른 ID, 회원 1,000명에 고르게)
// 도착률 고정(arrival-rate): 응답이 느려져도 보내는 양은 그대로 → 서버가 버티는지 그대로 드러난다
// 합격: 50 rps 구간 p95 < 300ms, 5xx 1% 미만 (노트북 환경 기준)
import { check } from 'k6';
import exec from 'k6/execution';
import { charge, orderIdOf } from './lib/payment.js';

const STAGE_SECONDS = Number(__ENV.STAGE_SECONDS || 120);
const RATES = (__ENV.RATES || '10,30,50,100').split(',').map(Number);

const scenarios = {};
RATES.forEach((rate, i) => {
  scenarios[`rps_${rate}`] = {
    executor: 'constant-arrival-rate',
    rate,
    timeUnit: '1s',
    duration: `${STAGE_SECONDS}s`,
    startTime: `${i * (STAGE_SECONDS + 10)}s`,   // 단계 사이 10초 쉼 (그래프에서 구간이 갈라져 보이게)
    preAllocatedVUs: Math.min(rate, 50),
    maxVUs: rate * 2,
    tags: { rps: String(rate) },
    env: { OFFSET: String(i * 1000000) },        // 단계마다 주문 ID 대역을 나눔
  };
});

const thresholds = {
  'http_req_duration{rps:50}': ['p(95)<300'],
  'http_req_failed{rps:50}': ['rate<0.01'],       // 5xx · 연결 실패 (409 같은 예상 거절은 제외)
};
RATES.forEach((rate) => {
  // 단계별 수치를 요약에 따로 보이게 (합격 기준은 50만)
  thresholds[`http_req_duration{rps:${rate}}`] = thresholds[`http_req_duration{rps:${rate}}`] || ['p(95)<60000'];
  thresholds[`http_req_failed{rps:${rate}}`] = thresholds[`http_req_failed{rps:${rate}}`] || ['rate<=1'];
});

export const options = {
  scenarios,
  thresholds,
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const n = Number(__ENV.OFFSET) + exec.scenario.iterationInTest;
  const amount = 1000 + (n % 30) * 1000;          // 1천~3만 원 (총액이 지갑 100만 원을 넘지 않게)
  const res = charge(orderIdOf(n), 10001 + (n % 1000), amount);
  check(res, { '결제 200-1': (r) => r.status === 200 });
}
