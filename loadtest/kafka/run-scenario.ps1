# Kafka 환불 시나리오 하나를 처음부터 끝까지 실행: 초기화 → 결제 준비(k6) → 취소 이벤트 파일 → Kafka에 넣기 → 인박스 적체 기록 → 검증
#   ./loadtest/kafka/run-scenario.ps1 -Label K2 -Count 5000
#   -Mode normal | dup | poison
# 먼저: docker compose --profile kafka up (README "Kafka 환불 테스트"), ./loadtest/run-payment.ps1 -Kafka
param(
    [Parameter(Mandatory)] [string]$Label,
    [Parameter(Mandatory)] [int]$Count,
    [ValidateSet('normal', 'dup', 'poison')] [string]$Mode = 'normal'
)
$ErrorActionPreference = 'Stop'
$lt = Split-Path -Parent $PSScriptRoot
$runId = [int]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() % 100000000)
$topic = 'cartmate.order.order-canceled'

Write-Host "== [$Label] 초기화 (RUN_ID=$runId)"
docker exec cartmate-lt-mysql sh -c "mysql -uroot -ploadtest --default-character-set=utf8mb4 < /sql/reset-seed.sql" 2>$null | Out-Null

Write-Host "== [$Label] 취소할 결제 $Count 건 만들기"
docker run --rm -m 256m -v "$lt/scripts:/scripts:ro" -e BASE_URL=http://host.docker.internal:18983 `
    -e RUN_ID=$runId -e COUNT=$Count grafana/k6:2.3.0 run --quiet /scripts/kafka-prepare.js 2>&1 |
    Select-String "✗|checks_succeeded"

$file = "$($Label.ToLower()).txt"
python "$PSScriptRoot/make-events.py" --run-id $runId --count $Count --mode $Mode --out "$PSScriptRoot/out/$file"

Write-Host "== [$Label] Kafka에 넣기 $(Get-Date -Format HH:mm:ss)"
$sw = [Diagnostics.Stopwatch]::StartNew()
docker exec cartmate-lt-kafka sh -c "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic $topic --property parse.key=true --property 'key.separator=|' --producer-property linger.ms=5 < /events/$file"
Write-Host "   넣기 끝: $([int]$sw.Elapsed.TotalSeconds)초"

python "$PSScriptRoot/watch-inbox.py" --label $Label --expect $Count

Write-Host "== [$Label] 검증"
docker exec cartmate-lt-mysql sh -c "mysql -uroot -ploadtest --default-character-set=utf8mb4 < /sql/verify.sql" 2>$null
Write-Host "== [$Label] DLT 메시지 수"
foreach ($t in "$topic-dlt", "$topic.DLT") {
    $n = docker exec cartmate-lt-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:9092 --topic $t 2>$null |
        ForEach-Object { [int]($_ -split ':')[-1] } | Measure-Object -Sum
    Write-Host "   $t : $($n.Sum)"
}
