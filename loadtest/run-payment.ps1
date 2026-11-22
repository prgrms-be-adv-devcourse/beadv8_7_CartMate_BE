# 결제 서비스를 부하 테스트 설정으로 실행 (IntelliJ 없이 jar로, 힙 384MB)
#   먼저: docker compose -f loadtest/docker-compose.yaml up -d mysql redis  (README 2단계)
#   jar  : ./gradlew :payment-service:bootJar
# 연결 대상은 전부 loadtest 컨테이너 (공용 서버 아님). 프로필을 dev가 아닌 loadtest로 둬서 application-dev.yaml(공용 DB 설정)을 읽지 않는다
param(
    [switch]$Kafka,                 # Kafka 환불 테스트: 로컬 Kafka(localhost:29092)에서 취소 이벤트를 받는다
    [int]$InboxBatchSize = 100      # 인박스 워커가 1초에 처리하는 최대 건수 (기본 100 = 운영 기본값)
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Get-ChildItem "$root/payment-service/build/libs" -Filter 'payment-service-*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
if (-not $jar) { throw 'jar가 없습니다. 먼저 ./gradlew :payment-service:bootJar' }

New-Item -ItemType Directory -Force "$PSScriptRoot/logs" | Out-Null

$env:SPRING_PROFILES_ACTIVE = 'loadtest'
$env:PAYMENT_SERVICE_PORT = '18983'                   # 평소 개발용 8983과 같이 떠 있어도 겹치지 않게
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:23306/cartmate_payment?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = 'loadtest'          # 시험 컨테이너 전용 값
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'update'
$env:SPRING_JPA_OPEN_IN_VIEW = 'false'
$env:REDIS_CACHE_PORT = '26379'
$env:REDIS_CORE_PORT = '26379'
$env:REDIS_DATABASE = '0'
$env:REDIS_PASSWORD = ''
$env:KAFKA_ENABLED = if ($Kafka) { 'true' } else { 'false' }   # 차감 · 되돌리기는 Kafka를 쓰지 않는다 (지갑은 SQL 시드)
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:29092'
$env:CUSTOM_INBOX_PROCESSOR_BATCH_SIZE = "$InboxBatchSize"
$env:INTERNAL_API_KEY = 'loadtest-internal-key'        # k6 스크립트 기본값과 같음
$env:SENTRY_DSN = ''                                   # 부하 중 오류를 Sentry로 보내지 않음 (무료 한도)

# 관측: 로그 파일(JSON) → Alloy → Cloud Loki, trace · 메트릭(OTLP) → Alloy → Cloud
$env:LOGGING_FILE_NAME = "$PSScriptRoot/logs/payment-service.log"
$env:LOGGING_STRUCTURED_FORMAT_FILE = 'logstash'
$env:LOGGING_THRESHOLD_CONSOLE = 'WARN'                # 콘솔 출력이 부하를 먹지 않게
$env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = 'http://localhost:4318/v1/traces'
$env:MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED = 'true'
$env:MANAGEMENT_OTLP_METRICS_EXPORT_URL = 'http://localhost:4318/v1/metrics'
$env:MANAGEMENT_OTLP_METRICS_EXPORT_STEP = '15s'
$env:MANAGEMENT_OTLP_METRICS_EXPORT_BASE_TIME_UNIT = 'seconds'   # 기본은 밀리초 → 이름이 http_server_requests_milliseconds_*가 됨. Prometheus 관례(초)에 맞춘다
$env:SERVER_TOMCAT_MBEANREGISTRY_ENABLED = 'true'               # Tomcat 요청 스레드 지표(tomcat_threads_busy)는 이걸 켜야 나온다
# 지연 히스토그램 구간 (p95 · p99 계산용). 구간을 정해 둬서 시계열 수가 적다
$env:MANAGEMENT_METRICS_DISTRIBUTION_SLO_HTTP_SERVER_REQUESTS = '25ms,50ms,100ms,200ms,300ms,500ms,1s,2s,5s'

Write-Host "결제 서비스 실행: $($jar.Name) → http://localhost:18983, Kafka=$($env:KAFKA_ENABLED), 인박스 배치=$InboxBatchSize (멈추려면 Ctrl+C)"
java -Xms256m -Xmx384m -XX:MaxMetaspaceSize=192m -Xss512k -jar $jar.FullName
