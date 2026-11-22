# 결제 서비스 부하 테스트 결과

실행 방법은 [README.md](README.md). 숫자는 노트북에서 잰 것이다. 운영 성능을 뜻하지 않는다.

## 발표 요약 (2026-11-23 최종 발표용)

| 질문 | 답 | 근거 (캡처) |
|---|---|---|
| 동시 요청에도 돈이 정확한가? | **예.** 결제 · 취소 · Kafka 환불 약 5만 건에서 돈 불변식 위반 0건 (중복 결제 · 음수 잔액 · 중복 환불 없음) | [대시보드](screenshots/01-payment-loadtest/01-dashboard.png) |
| 결제 API는 얼마나 버티나? | 초당 100건까지 p95 77ms, 5xx 0건 (기준 300ms) | [지연](screenshots/01-payment-loadtest/03-latency.png) · [초당 요청](screenshots/01-payment-loadtest/02-rps.png) |
| 어디가 약한가? | ① 같은 지갑에 동시 결제가 몰리면 데드락으로 일부 거절 (100건 중 54~62건 성공) ② Kafka 환불은 **초당 약 20건**이 한계 → 5,000건이 몰리면 다 처리하는 데 약 4분 | [MySQL 락](screenshots/01-payment-loadtest/06-mysql-lock.png) · [인박스 적체](screenshots/02-kafka-refund/02-inbox-backlog.png) · [220초 기다린 trace](screenshots/02-kafka-refund/06-trace-220s-wait.png) |
| 모니터링이 쓸모 있었나? | 대시보드 → 로그 → trace로 원인까지 추적. 거짓 알림 · 거짓 성공 로그 3개를 찾아 고침 (PR #179) | [경고 · 오류 로그](screenshots/01-payment-loadtest/08-warn-error-logs.png) |
| 비용은? | Grafana Cloud 무료 한도의 0.1% 미만 (부하테스트 모드: 정상 trace 1%) | 아래 "Cloud 사용량" |

## 환경

| 항목 | 값 |
|---|---|
| 날짜 · 커밋 | 2026-11-22 01:55~02:11 (KST), dev `9fc8ae6f` |
| 노트북 | 메모리 7.3GB, 논리 CPU 16. 테스트 중 남은 메모리 약 0.8GB (로컬 서비스 4개 · 컨테이너 · demo-app은 내림, k8s 자체는 켜 둠) |
| 앱 | 결제 서비스 jar, 힙 384MB, Tomcat 스레드 50, DB 커넥션 풀 10, Kafka 끔 |
| DB | MySQL 8.4 컨테이너 512MB (버퍼 풀 128MB) |
| 수집 | Alloy 부하테스트 모드 → Grafana Cloud (`env="loadtest"`) |

## 1. 결제 API 부하 (S1~S5)

캡처: [screenshots/01-payment-loadtest/](screenshots/01-payment-loadtest/)

### 결과 요약

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

## 2. Kafka 환불 이벤트 (K1~K4, 2026-11-22 03:28~03:47)

캡처: [screenshots/02-kafka-refund/](screenshots/02-kafka-refund/)

주문 서버 대신 **주문 취소 이벤트(`OrderCanceledEvent`)를 Kafka에 직접 넣었다.** 형식은 Outbox 봉투와 같다. 결제 서비스는 받은 이벤트를 `inbox_event`에 저장하고, 인박스 워커가 환불한다.
환경: 결제 서비스 + Kafka 4.1 단일 브로커(힙 384MB, 파티션 3) + kafka-exporter. 실행은 `kafka/run-scenario.ps1`.

| 시나리오 | 넣은 메시지 | 결과 | 불변식 위반 |
|---|---|---|---|
| K1 연기 | 10 | 10건 환불, 2초 | 0 |
| **K2 몰아넣기 (인박스 배치 100, 기본값)** | 5,000 | 최대 적체 4,468건, **다 처리까지 269초 → 초당 18.6건** | 0 |
| **K2 몰아넣기 (인박스 배치 500)** | 5,000 | 최대 적체 4,380건, **224초 → 초당 22.3건 (+20%)** | 0 |
| K3 중복 (같은 eventId 3번 + 다른 eventId 1번) | 2,000 (주문 500) | 같은 eventId 1,000개는 인박스에서 바로 걸러짐, **환불 정확히 500건** | 0 |
| K4 깨진 메시지 10개 섞기 | 1,010 | 깨진 10개는 재시도 없이 **바로 DLT**, 정상 1,000건은 막히지 않고 모두 환불 | 0 |

### 알게 된 것

1. **돈은 정확하다.** 같은 이벤트가 여러 번 와도(Kafka 재전송 · 다른 eventId로 재발행) 환불은 결제마다 1번이다. 인박스의 eventId 유니크와 환불 키 유니크가 각각 막는다.
2. **처리량 한계는 초당 약 20건이다. 병목은 "한 건씩 순서대로" 처리하는 구조다.**
   - 이론상 한계(1초마다 100건)는 초당 100건이지만 실제는 18.6건이었다.
   - trace를 보면 이벤트 1건 처리(`inbox.process`)에 약 32ms가 걸린다. 환불 · 지갑 · 원장 · 처리 기록 쿼리가 여러 번 나간다. 인박스 워커는 이것을 **한 스레드에서 순서대로** 한다.
   - 그래서 배치를 5배(100 → 500)로 키워도 20%만 빨라졌다. 배치 사이 1초 쉬는 시간만 줄었다.
   - trace 캡처: 받은 뒤 처리되기까지 **220초 기다린 이벤트.** 처리 자체는 33ms였다.
3. **Kafka lag만 보면 적체를 놓친다.**
   - 결제 서비스는 메시지를 받자마자 인박스에 저장하고 오프셋을 커밋한다. 그래서 Kafka lag은 금방 0이 된다.
   - 실제 대기는 인박스(NEW)에 쌓인다 → 운영 모니터링에는 **인박스 NEW 건수 지표**가 필요하다.
   - 지금은 시험용 스크립트(`kafka/watch-inbox.py`)가 1초마다 DB에서 재서 로그로 보낸다.
4. **깨진 메시지가 정상 처리를 막지 않는다.** JSON 오류는 재시도하지 않고 바로 `<토픽>-dlt`로 보낸다(`KafkaConfig`). 그리고 DLT 토픽이 **원래 토픽과 파티션 수가 같아야** 한다는 것을 확인했다. DLT는 같은 파티션 번호로 보내기 때문이다.

### 개선 방향 (다음 작업 후보, 이번에 바꾸지 않음)

| 방법 | 예상 효과 | 고려할 점 |
|---|---|---|
| 인박스 워커 병렬화 (토픽 · 파티션 · 주문 ID별로 나눠 여러 스레드) | 스레드 수만큼 처리량 증가 | 같은 주문 이벤트의 순서를 지켜야 함. common 인박스라 팀 공통 변경 |
| 결제 서비스 인스턴스 늘리기 | 현재 ShedLock으로 워커가 한 대에서만 돌아 **효과 없음** | 락 범위를 나눠야 늘어남 |
| 건당 처리 시간 줄이기 (쿼리 수 · 인덱스) | 32ms → 더 짧게 | 환불 · 지갑 코드(지갑은 sdw51 담당) |
| 인박스 NEW 건수 메트릭 + 알림 | 적체를 운영에서 바로 봄 | common 인박스에 Micrometer 게이지 1개 |

### 이번 실행 수치 (참고)

- 메모리: Kafka 347MB, MySQL 202MB, Alloy 60MB, exporter 4MB. 노트북 남은 메모리 약 0.8GB에서 실행
- Kafka에 넣는 속도: 5,000개를 약 11초에 넣음 (console-producer)
