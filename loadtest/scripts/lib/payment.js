// 결제 서비스 내부 API 호출 · 공통 지표 (k6)
import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:18983';
const API_KEY = __ENV.INTERNAL_API_KEY || 'loadtest-internal-key';

// 실행마다 다른 주문 ID 대역 (같은 DB에서 다시 돌려도 겹치지 않게). 9자리 * 10^7 → Long 범위 안
export const RUN_ID = Number(__ENV.RUN_ID || Math.floor(Date.now() / 1000) % 100000000);
export const orderIdOf = (n) => RUN_ID * 10000000 + n;

const params = (name) => ({
  headers: { 'Content-Type': 'application/json', 'X-Internal-Api-Key': API_KEY },
  tags: { name },
  // 409 · 404 같은 예상된 거절은 http_req_failed(실패)로 세지 않는다. 5xx만 따로 센다
  responseCallback: http.expectedStatuses({ min: 200, max: 499 }),
});

// 응답 결과 코드별 개수 (200-1, 409-…, 500-… 등) → 요약에서 바로 본다
export const results = new Counter('payment_results');
export const serverErrors = new Counter('server_errors_5xx');

function record(res, api) {
  let code = String(res.status);
  try {
    code = res.json('resultCode') || code;
  } catch (e) {
    // JSON이 아니면 HTTP 상태만
  }
  results.add(1, { api, code });
  if (res.status >= 500) {
    serverErrors.add(1, { api });
  }
  return res;
}

export function charge(orderId, memberId, amount, orderType = 'INDIVIDUAL_GROUP') {
  const body = JSON.stringify({ orderType, orderId, memberId, amount });
  return record(http.post(`${BASE_URL}/internal/payments`, body, params('charge')), 'charge');
}

export function compensate(orderId, memberId, orderType = 'INDIVIDUAL_GROUP') {
  const url = `${BASE_URL}/internal/payments/compensate?orderType=${orderType}&orderId=${orderId}&memberId=${memberId}`;
  return record(http.post(url, null, params('compensate')), 'compensate');
}

// 가상 사용자들이 같은 순간에 요청하도록 맞추는 출발선: 모두 at(밀리초 시각)까지 기다린다
export function waitUntil(at) {
  const wait = at - Date.now();
  if (wait > 0) {
    sleep(wait / 1000);
  }
}
