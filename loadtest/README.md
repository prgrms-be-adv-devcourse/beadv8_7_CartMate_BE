# 결제 서비스 부하 테스트

결제 내부 API(`POST /internal/payments` 차감, `POST /internal/payments/compensate` 되돌리기)에 노트북에서 부하를 준다. 결과는 Grafana Cloud에서 본다.

- **목표**: (1) 돈 정확성: 동시에 와도 한 번만 차감하고, 잔액이 음수가 되지 않는다. (2) 부하 중 모니터링(지연 · 커넥션 풀 · 오류 · trace)이 동작한다.
- **도구**: [k6](https://k6.io). JMeter보다 메모리를 훨씬 적게 쓰고, 시나리오가 JS 파일이라 리뷰하기 쉽다. 설치 없이 docker 이미지로 돌린다.
- **환경**: 노트북 로컬 전용 MySQL · Redis 컨테이너. 팀 공용 서버는 운영과 같은 서버라 **절대 쓰지 않는다**. Kafka는 끈다(차감 · 되돌리기는 Kafka를 쓰지 않음).
- 결과 기록: [RESULTS.md](RESULTS.md)

## 구성과 메모리 (약 1.5GB)

| 구성 | 상한 | 비고 |
|---|---|---|
| 결제 서비스 (jar, 포트 **18983**) | 힙 384MB, 프로세스 약 550MB | 평소 개발용 8983과 겹치지 않게 |
| MySQL 8.4 (포트 23306) | 512MB (버퍼 풀 128MB, performance_schema 끔) | 볼륨 없음 → `down` 하면 데이터 사라짐 |
| Redis (포트 26379) | 64MB | common 락(Redisson) · 캐시용. cache · core 둘 다 이것 하나 |
| Alloy | 256MB | 로그 · trace · 메트릭 → Grafana Cloud |
| k6 (docker) | 256MB | |

**테스트 전에 메모리를 비운다.** 노트북 7.3GB 중 평소 남는 게 0.5GB 안팎이라, 그대로 돌리면 앱이 디스크로 밀려나(페이징) 지연이 수 초로 뛴다. 그러면 노트북을 재는 셈이 된다.
- IntelliJ와 쓰지 않는 브라우저 탭을 닫는다.
- Docker Desktop → Settings → Kubernetes에서 **끈다** (demo-app 파드와 k8s 구성 요소가 1GB 넘게 먹음).
- 안 쓰는 컨테이너는 stop만 한다(삭제하지 않음): `docker stop cartmate-local-mysql cartmate-local-redis-core cartmate-local-redis-cache mailpit`
- 평소 개발용 결제 서비스(8983)가 떠 있으면 내린다.

## Grafana Cloud 용량

Alloy는 **부하테스트 모드**로 뜬다(`docker-compose.yaml`의 `OBS_*`).
- 정상 trace는 1%만 남긴다. 오류 trace와 느린 trace(1초 이상)는 100% 남긴다.
- 업무 이벤트가 없는 INFO 로그는 버린다.
- 서비스별 로그는 초당 200줄을 넘지 않게 한다.

| 데이터 | 이번 테스트 전체 예상 | 무료 한도 |
|---|---|---|
| trace | 요청 1천 건당 약 4KB → 전체 약 3만 건에 0.1MB 수준 | 50GB/월 |
| 로그 | `payment.charge` 이벤트 줄(건당 약 0.4KB)만 → 약 15MB | 50GB/월 |
| 메트릭 | 시계열 약 200개 (필요한 지표만 남김, `alloy/config.alloy`의 keep 규칙) | 1만 개 |

끝나면 Cloud 포털 → **Usage**에서 실제 사용량을 RESULTS.md에 적는다.

## 실행 순서 (PowerShell, 저장소 루트에서)

### 1. jar 빌드 (코드가 바뀌었을 때만)

```powershell
./gradlew :payment-service:bootJar
```

### 2. MySQL · Redis 띄우기

```powershell
docker compose -f loadtest/docker-compose.yaml up -d --wait mysql redis
```

### 3. Grafana Cloud 연결 정보 (처음 한 번 + 매 실행마다 토큰)

1. `loadtest/.env.example`을 `loadtest/.env`로 복사하고 URL과 사용자 번호를 채운다. 값은 Cloud 포털 → 스택 → Loki · Prometheus · OpenTelemetry의 Details에 있다. `.env`는 gitignore된다.
2. 토큰을 만든다. Cloud 포털 → Access Policies → `cartmate-alloy` → **Add token**, 이름은 `alloy-loadtest`. 쓰기 권한(logs · metrics · traces)만 있는 정책이다. **테스트가 끝나면 지운다.**
3. 토큰을 화면에 보이지 않게 넣는다. 파일 · 기록에 남지 않는다.

```powershell
$s = Read-Host "Grafana Cloud 토큰" -AsSecureString
$env:GC_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
docker compose -f loadtest/docker-compose.yaml up -d alloy
```

확인: http://localhost:12345 (Alloy 화면)에서 `loki.write.cloud`, `otelcol.exporter.otlphttp.cloud`, `prometheus.remote_write.cloud`가 모두 Healthy인지 본다.

### 4. 결제 서비스 실행 (창 하나를 따로 열어 둔다)

```powershell
./loadtest/run-payment.ps1
```

- 프로필은 `loadtest`다. 그래서 `application-dev.yaml`(공용 DB 설정)을 읽지 않는다.
- 테이블은 `ddl-auto: update`가 만든다.
- 처음 뜰 때 1분쯤 걸린다. `loadtest/logs/payment-service.log`에 `Started PaymentServiceApplication`이 보이면 준비된 것이다.

### 5. 대시보드 (처음 한 번)

Cloud Grafana → Dashboards → New → **Import**에서 `loadtest/grafana/payment-loadtest.json`을 올린다. 폴더는 `CartMate`로 한다.

### 6. 시나리오 실행 (각각 시드 초기화 → k6 → 검증)

시나리오마다 아래 3줄을 반복한다. `<스크립트>` 자리에 아래 표의 파일 이름을 넣는다.

```powershell
docker exec cartmate-lt-mysql sh -c "mysql -uroot -ploadtest --default-character-set=utf8mb4 < /sql/reset-seed.sql"
docker run --rm -m 256m -v "${PWD}/loadtest/scripts:/scripts:ro" -v "${PWD}/loadtest/results:/results" -e BASE_URL=http://host.docker.internal:18983 grafana/k6:2.3.0 run --summary-export=/results/<스크립트>.json /scripts/<스크립트>.js
docker exec cartmate-lt-mysql sh -c "mysql -uroot -ploadtest --default-character-set=utf8mb4 < /sql/verify.sql"
```

| 순서 | 스크립트 | 부하 | 걸리는 시간 | 합격 기준 |
|---|---|---|---|---|
| S1 | `s1-smoke` | 1명, 30초 | 30초 | 전부 200. **Cloud 대시보드에 데이터가 보이는지** 먼저 확인 |
| S2 | `s2-load` | 초당 10 → 30 → 50 → 100 요청, 단계마다 2분 | 약 9분 | 50 rps 구간 p95 < 300ms, 5xx · 연결 실패 1% 미만 |
| S3 | `s3-same-order` | 100명이 같은 주문으로 동시에 결제 × 20라운드 | 약 35초 | 5xx 0건, verify V1 · V3 · V4 = 0 (주문마다 결제 1건) |
| S4 | `s4-same-wallet` | 100명이 한 지갑(10만 원)의 다른 주문(5천 원)을 동시에 × 5라운드 | 약 15초 | 5xx 0건, verify V2 · V3 · V10 = 0 (잔액 음수 없음). 라운드당 성공 수(`s4_paid`)를 기록 |
| S5 | `s5-compensate` | 결제 10건 각각에 되돌리기를 100명이 동시에 | 약 20초 | 5xx 0건, verify V7 · V8 · V9 = 0 (결제마다 환불 1번) |

- k6 화면의 `THRESHOLDS`에 ✗가 있으면 기준 실패다.
- `verify.sql`은 `violations`가 모두 0이어야 한다.
- 옵션은 `-e` 환경 변수로 바꾼다:
  - `-e ROUNDS=…`, `-e VUS=…`: 라운드 수 · 동시 사용자 수
  - `-e RATES=10,30 -e STAGE_SECONDS=60`: S2 단계를 줄일 때
- 시나리오가 도는 동안 대시보드를 보고, 끝나면 화면을 캡처한다. 지연 그래프와, 오류 로그에서 traceId를 눌러 연 trace 하나.

### 7. 정리

```powershell
# 결제 서비스 창에서 Ctrl+C
docker compose -f loadtest/docker-compose.yaml down
Remove-Item Env:GC_TOKEN
```

- Cloud 포털에서 `alloy-loadtest` 토큰을 **지운다**.
- Usage를 확인한다.
- 멈춰 둔 컨테이너와 Kubernetes는 필요하면 다시 켠다.

## 파일

| 파일 | 내용 |
|---|---|
| `docker-compose.yaml` | MySQL · Redis · Alloy (메모리 상한) |
| `alloy/config.alloy` | 파일 로그 · OTLP trace · OTLP 메트릭 · MySQL · Redis 지표 → Grafana Cloud (`env="loadtest"`) |
| `run-payment.ps1` | 결제 서비스를 시험 설정으로 실행 (로컬 DB · Kafka 끔 · 관측 연결 · 지연 히스토그램) |
| `sql/reset-seed.sql` | 결제 · 지갑 데이터 비우고 시험 지갑 1,035개 넣기 + 시작 잔액 기록(`lt_seed`) |
| `sql/verify.sql` | 돈 불변식 10개 검사 |
| `scripts/s1~s5*.js`, `scripts/lib/payment.js` | k6 시나리오 · 공통 호출 |
| `grafana/payment-loadtest.json` | Cloud 대시보드 |
| `results/` | k6 요약 JSON (gitignore) |

## 주의

- 토스 충전 승인(`/api/wallets/me/charges/confirm`)은 외부 토스 API를 부르므로 **부하 대상이 아니다**.
- 노트북에서 잰 수치는 운영 성능이 아니다. 비교와 병목 위치를 찾는 데 쓰고, 결과에 환경(남은 메모리 등)을 같이 적는다.
- Kafka를 켜고 환불 이벤트 처리량 · 소비 지연을 보는 2단계는 메모리가 1.3GB 더 필요하다(브로커 + Kafka UI). 이번 범위 밖이다.
