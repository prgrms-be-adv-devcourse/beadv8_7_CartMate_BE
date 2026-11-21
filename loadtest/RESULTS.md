# 결제 서비스 부하 테스트 결과

실행 방법은 [README.md](README.md). 숫자는 노트북에서 잰 것이다. 운영 성능을 뜻하지 않는다.

## 환경

| 항목 | 값 |
|---|---|
| 날짜 · 커밋 | 2026-11-22 01:55~02:11 (KST), dev `9fc8ae6f` |
| 노트북 | 메모리 7.3GB, 논리 CPU 16. 테스트 중 남은 메모리 약 0.8GB (로컬 서비스 4개 · 컨테이너 · demo-app은 내림, k8s 자체는 켜 둠) |
| 앱 | 결제 서비스 jar, 힙 384MB, Tomcat 스레드 50, DB 커넥션 풀 10, Kafka 끔 |
| DB | MySQL 8.4 컨테이너 512MB (버퍼 풀 128MB) |
| 수집 | Alloy 부하테스트 모드 → Grafana Cloud (`env="loadtest"`) |

## 결과 요약

| 시나리오 | 요청 수 | p50 | p95 | p99 | 5xx | 불변식 위반 | 합격 |
|---|---|---|---|---|---|---|---|
| S1 연기 테스트 | 52 | 54ms | 106ms | – | 0 | 0 | ✅ |
| S2 10 rps (2분) | 1,201 | 36ms | 89ms | 257ms | 0 | 0 | ✅ |
| S2 30 rps | 3,601 | 30ms | 51ms | 67ms | 0 | 0 | ✅ |
| **S2 50 rps (기준: p95 < 300ms)** | 6,001 | 30ms | **52ms** | 80ms | 0 | 0 | ✅ |
| S2 100 rps | 12,001 | 36ms | 77ms | 198ms | 0 | 0 | ✅ |
| S3 같은 주문 동시 (100명 × 20) | 2,000 | 127ms | 338ms | – | 0 | 0 | ✅ |
| S4 같은 지갑 경합 (100명 × 5) | 500 | 545ms | 1.16s | – | 0 | 0 | ✅ (단, 아래 발견 1) |
| S5 되돌리기 중복 (100명 × 10) | 1,010 | 141ms | 386ms | – | 0 | 0 | ✅ (단, 아래 발견 2) |

- S2 전체 22,804건 모두 성공. 100 rps에서도 지연이 늘지 않았다. 이 환경에서 결제 내부 API의 한계는 100 rps보다 위다. 10 rps 구간 p99가 더 높은 것은 JVM 예열 때문이다.
- S3: 라운드마다 성공 1건, 기존 결제 반환(DUPLICATE, 200) 90건, `PAYMENT_IN_PROGRESS`(409) 9건. **주문마다 결제 1건 · 차감 1번**.
- S4: 라운드마다 최대 20건이 가능했지만 **5라운드 합계 성공 54건(평균 10.8건)**. 나머지 446건은 409 `PAYMENT_CONFLICT`(데드락 374건, 낙관적 락 실패 72건). **잔액은 정확**했다(음수 0, 차감 합계 = 잔액 변화).
- S5: 결제 10건 모두 정확히 1번 · 전액 환불. 동시 요청 중 90건은 환불 키 충돌로 409, 900건은 이미 환불된 뒤라 아무것도 하지 않고 200.

### Grafana Cloud 사용량 (Alloy 전송 기준, 이번 실행 전체)

| 데이터 | 양 | 무료 한도 대비 |
|---|---|---|
| 로그 | 27,024줄 · 14MB (거의 다 `payment.charge` 업무 이벤트) | 50GB/월의 0.03% |
| trace | 26,364개 중 272개 보관 (1.03%: 1% 표본 + 오류 · 느린 trace) | 매우 작음 |
| 메트릭 | 샘플 2,739개 · 132KB | 시계열 수십 개 (한도 1만) |

## 발견한 것

### 1. 같은 지갑에 동시 결제가 몰리면 잔액이 충분한 결제도 거절된다 (S4)

- **결과**: 잔액으로 100건이 가능했는데 54건만 성공했다. 46건은 돈이 있었는데도 409를 받았다.
- **원인** (`PaymentConflictExceptionHandler` 주석의 "남은 과제"와 같다):
  - 지갑 차감이 엔티티 수정(@Version)으로 되어 있다. 그래서 동시 요청끼리 낙관적 락 실패가 난다.
  - 홀딩 · 거래 기록 INSERT의 외래 키 검사가 지갑 행에 공유 락을 잡는다. 이 락이 지갑 UPDATE의 배타 락과 엇갈려 MySQL 데드락이 난다(374건).
- **영향**: 돈은 안전하다. 한 사람이 여러 주문을 동시에 결제하는 경우는 드물다. 하지만 공동 주문 분담금이나 재시도가 겹치면 사용자가 다시 시도해야 한다.
- **해결 방향**: 결제 차감 · 환불을 조건부 UPDATE(`WalletRepository.decreaseBalance` · `increaseBalance`, 출금이 이미 쓰는 방식)로 바꾼다. 그러면 같은 지갑 요청이 행 락 하나로 줄을 서게 된다. 바꾼 뒤 S4를 다시 돌려 성공 100건이 나오는지 확인한다.
- 지갑은 sdw51 담당이라 함께 정해야 한다.

### 2. 동시 되돌리기에서 진 요청이 `payment.compensate FAILED`(ERROR)로 기록된다 → 거짓 알림 (S5)

- 같은 결제에 되돌리기가 동시에 오면 하나만 환불한다. 진 요청은 환불 키 중복으로 409를 받는다. 돈은 정확하다.
- 문제는 진 요청이 `event=payment.compensate, result=FAILED`를 **ERROR 레벨**로 남긴다는 것이다(90건).
- Grafana 알림 "결제 보상 실패"가 바로 이 조건이라, 실제 실패가 없는데도 알림이 울린다.
- 해결 방향: 환불 키 중복(`PAYMENT_REFUND_IN_PROGRESS`)은 `result=IN_PROGRESS` 등으로 따로 남기고 WARN 이하로 낮춘다.

### 3. 롤백된 결제도 `payment.charge SUCCESS`로 기록된다 (S4)

- S4에서 `payment.charge result=SUCCESS` 로그가 500줄 나왔다. 실제로 커밋된 결제는 54건이다.
- 원인: `PaymentChargeUseCase.logCharge`가 트랜잭션 안에서 커밋 전에 로그를 남긴다. 그 뒤 커밋 단계(flush)에서 데드락 · 낙관적 락으로 롤백돼도 로그는 남는다.
- 영향: 로그로 센 결제 수가 실제보다 커진다(대시보드 "결제 결과" 패널, 장애 분석).
- 해결 방향: 커밋 후(`TransactionSynchronization.afterCommit`)에 SUCCESS를 남기거나, 1번이 해결되면 사라진다(충돌 자체가 없어짐).

### 4. 부하 테스트가 팀 Discord 알림을 울릴 수 있다 (운영 주의)

- Cloud 알림 3개의 쿼리가 `{service=~".+"}`이다. 그래서 `env="loadtest"` 데이터에도 걸린다.
- 이번 실행에서 **"서비스 ERROR 급증"(payment-service)과 "결제 보상(되돌리기) 실패"가 02:11에 발생 → 02:16에 해결**됐다(Cloud 알림 기록). Discord로는 가지 않았는데, 누군가 00:13~04:12에 `grafana_folder=CartMate` 알림을 끄는 사일런스(설명 "부하테스트")를 걸어 두었기 때문이다. 사일런스가 없었다면 팀 채널에 거짓 알림이 갔다.
- 해결 방향: 알림 쿼리에 `env!="loadtest"`를 넣는다(`observability/grafana-cloud/alerts/*.json`, 로컬 provisioning도 같이). 넣기 전에는 부하 테스트 전에 팀에 미리 알린다.

### Grafana Cloud에서 확인한 것

- 대시보드 `CartMate 결제 부하 테스트`(CartMate 폴더)에 초당 요청 · 지연 p50/p95/p99 · 커넥션 풀 · JVM 힙/CPU · MySQL 락 대기 · 결제 결과 로그 · 경고/오류 로그가 테스트 시간대(01:55~02:13)대로 그려진다.
- S4 구간에서 MySQL 행 락 대기가 초당 15건까지 튀고, 결제 결과 패널에 `payment.conflict`가 보인다.
- Tempo에 `http post /internal/payments` trace가 남아 있다(1% 표본).
- Tomcat 스레드 지표 이름은 `tomcat_threads_busy` · `tomcat_threads_config_max`였다(단위 접미사 없음). keep 규칙과 대시보드를 고친 뒤 재실행에서 확인: S4 · S5(동시 100명)에서 **요청 스레드 50개가 모두 찼다** → 동시 요청이 50을 넘으면 Tomcat 대기열에서 기다린다.

### 측정 중 고친 것 (앱 코드 아님)

- Micrometer OTLP의 기본 시간 단위가 밀리초라 메트릭 이름이 `http_server_requests_milliseconds_*`였다. 그래서 keep 규칙에 걸려 버려졌다. → `MANAGEMENT_OTLP_METRICS_EXPORT_BASE_TIME_UNIT=seconds`로 고쳤다.
- Tomcat 스레드 지표는 `SERVER_TOMCAT_MBEANREGISTRY_ENABLED=true`여야 나온다.
- 남은 메모리 0.4GB 상태(사전 확인)에서는 S1 평균이 2초, 최대 14초였다. 0.8GB로 늘리자 평균 83ms가 됐다. 노트북 측정은 메모리 상태를 꼭 같이 적는다.

## 수정 후 재실행 (2026-11-22 03시, 발견 2 · 3 · 4 수정)

| 수정 | 내용 | 재실행 확인 |
|---|---|---|
| 발견 2 | 되돌리기에서 환불 키가 겹치면 `payment.compensate result=IN_PROGRESS`, WARN으로 남김 (`PaymentRefundUseCase`). 응답은 그대로 409 `PAYMENT_REFUND_IN_PROGRESS` | S5: FAILED **0줄**, IN_PROGRESS 90줄(WARN), 환불은 결제마다 1번 |
| 발견 3 | `payment.charge SUCCESS`를 커밋 후(`afterCommit`)에 남김 (`PaymentChargeUseCase`) | S4 + S5 준비 결제: SUCCESS 72줄 = 실제 결제 72건(62 + 10). 수정 전에는 500줄 |
| 발견 4 | 알림 3개 쿼리에 `env!="loadtest"` (`observability/grafana-cloud/alerts/*.json`, 로컬 `cartmate-alerts.yaml`, **Cloud 알림에도 적용함**) | 부하 테스트 데이터로는 알림이 울리지 않음 |

- 재실행 S4 성공 수는 62건이다(수정 전 54건). 발견 1은 그대로라 실행마다 달라진다. 5xx는 0건, 불변식 위반은 0건이다.
- 결제 서비스 단위 테스트 51개 통과.
