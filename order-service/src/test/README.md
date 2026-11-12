# order-service 테스트 규칙

cart · order · abuse · 매칭 테스트를 쓰기 전에 읽는다. 아래 규칙은 모두 실제로 겪은 문제(맨 아래 사고 기록)에서 나왔다.

## 1. 어떤 테스트로 쓸까

| 확인하려는 것 | 쓰는 것 |
|---|---|
| 도메인 규칙, UseCase 분기 | Spring 없는 단위 테스트(필요하면 Mockito). UseCase는 `@BeforeEach`에서 `new`로 만들고 `Clock.fixed(...)`를 넣는다 |
| 저장소 · QueryDSL 쿼리 | `@CartMateJpaTest` |
| DB · 락 · 이벤트 릴레이를 거치는 흐름, 컨트롤러 | `@CartMateIntegrationTest` |
| Kafka 송수신 | `@CartMateKafkaIntegrationTest` |

- `@SpringBootTest` · `@DataJpaTest`를 직접 쓰지 않는다. 공용 어노테이션 위에 `@MockitoBean` · `@MockitoSpyBean` · `@Import` · `@TestPropertySource`를 더하지 않는다. 하나만 달라도 Spring 컨텍스트가 새로 뜨고, 컨텍스트마다 DB 커넥션 풀을 쥔다.
- 이 빈만 다르게 동작시키고 싶다면 공용 대역을 쓴다. 기본은 실제와 같고 테스트에서만 동작을 바꾸는 빈이며, `global/outbox/ManualOutboxRelayConfig`의 `@Import`에 등록해 공용 컨텍스트를 그대로 둔다.
  - 예: `FakeOrderProductClient`의 덮어쓰기, `abuse/out/AbuseMemberStateFaults`(실패 주입)
- 단위 테스트에서 `@InjectMocks`를 쓰지 않는다. 테스트에 선언되지 않은 생성자 인자(예: `Clock`)에 null을 넣어 쓸 때 NPE가 난다.

## 2. 무엇을 테스트할까

"이 테스트가 없으면 깨져도 모를 규칙"만 쓴다.

- 쓴다: 돈(차감 · 되돌리기 · 환불 여부), 상태 전이, 기한 · 수량 경계, 거절 사유, 동시성, 서비스 간 계약(응답 필드 이름 · 타입), 인증 · 권한
- 쓰지 않는다: mock 위임만 verify, getter · 단순 매핑, 업무에 영향 없는 "없으면 예외", 하위 계층(도메인 단위)이 이미 지키는 규칙의 반복, Spring Data 파생 쿼리 그대로 확인
- 기존 테스트의 단정을 강화해서 해결되면 새 테스트 메서드를 만들지 않는다.

## 3. 공용 기반

| 필요 | 쓰는 것 |
|---|---|
| DB 정리 | 통합 · JPA 테스트는 `support/DatabaseCleaner`가 시작 전에 모든 테이블을 비운다. 테스트에서 지우는 코드를 쓰지 않는다. 연결 DB가 테스트 컨테이너가 아니면 지우지 않고 실패한다(로컬 `cartmate` DB 보호) |
| 테스트 DB · Kafka | `support/CartMateTestContainers`(MySQL), `support/CartMateKafkaContainer`(Kafka, Kafka 테스트 때만 기동). 둘 다 JVM 하나에서 한 번만 뜨는 싱글턴 |
| 현재 시각 | 운영 코드는 `Clock` 빈으로 `LocalDateTime.now(clock)`. 단위 테스트는 `Clock.fixed`, 통합 테스트는 시스템 시계 |
| 엔티티 만들기 | `order/support/OrderFixtures`, `cart/support/GroupPurchaseFixtures`, Fake 클라이언트 데이터는 `support/TestCatalog` |
| 동시 실행 | `support/ConcurrentRunner`(시간 안에 안 끝나면 실패, 허용한 예외 밖은 실패로 드러냄) |
| 아웃박스 릴레이 | `support/OutboxRelayHelper.relayUntil(...)` |
| 기다리기 | `Thread.sleep` 금지(ArchUnit이 막음). `LockWaits.awaitBlockedOn`, `KafkaTestTopics.awaitUntil · awaitConsumed · awaitRecord`처럼 상태가 될 때까지 짧게 확인하고 최대 시간이 지나면 실패 |

- 시간에 기대는 단정을 쓰지 않는다. "3초 안에 끝난다", "아직 잠겨 있다(lock_until > 지금)" 같은 단정은 기기가 바쁘면 깨진다. 순서(이 표시보다 먼저 끝났다)나 저장된 값의 차이로 바꾼다.
- `.java` 파일에는 주석을 쓰지 않는다(팀 규칙). 설명은 `@DisplayName`(한글)과 커밋 메시지에 쓴다.

## 4. 실행

- 작업 중에는 바꾼 테스트만 돌린다: `./gradlew :order-service:test --tests "<패키지나 클래스>.*"`
- dev에 병합하기 전에는 모듈 전체 컴파일과 바꾼 테스트만 돌린다: `./gradlew :order-service:compileTestJava`. 지운 helper나 바뀐 생성자처럼 다른 작업과 엮여 깨지는 것은 컴파일에서 잡힌다. 컴파일이 실패하면 병합하지 않는다.
- 모듈 전체 테스트는 로컬에서 돌리지 않는다. PR을 올리면 CI가 돌린다.

## 5. 컨텍스트 보고서 읽는 법

테스트가 끝나면 `order-service/build/reports/test-contexts.md`가 생기고, CI 요약 끝에도 붙는다.

- 컨텍스트마다 그 컨텍스트를 쓴 테스트 클래스와 구성(설정 · 프로필 · 속성 · mock/spy 빈)이 나온다.
- 공용 어노테이션 구성과 같으면 "공용", 다르면 "통합 후보"이고 공용과 무엇이 다른지가 적힌다.
- 통합 후보가 2개 이상이면 맨 위에 "통합 필요"가 뜬다. 테스트는 실패하지 않는다. 의도해서 하나 더 만든 컨텍스트까지 막지 않기 위해서다.
- "통합 필요"가 뜨면 후보들을 한 번에 공용으로 합친다.
  - mock/spy 빈은 공용 대역으로 바꾸거나, 실제 결과(DB 상태)로 검증하도록 바꾼다.
  - 다른 속성은 공용 기본값으로 맞춘다.
- 안전망: 컨텍스트 캐시 상한(`spring.properties`의 `spring.test.context.cache.maxSize`) × Hikari 풀 크기(`application-test.yaml`) ≤ MySQL `max_connections`(`CartMateTestContainers`). `support/TestConnectionBudgetTest`가 이 식을 검사한다. 셋 중 하나를 바꾸면 나머지도 맞춘다.

## 6. 사고 기록

새 사고를 겪으면 아래에 같은 형식으로 더한다.

### 2026-11-05 · CI가 Too many connections로 15분 제한에 걸림 (PR #82)
- 증상: 테스트가 MySQL 연결을 얻지 못하고 CI가 시간 초과로 끝났다.
- 원인: 테스트마다 속성 · `@Import` · `@MockitoBean`이 조금씩 달라 캐시된 Spring 컨텍스트가 22개가 됐다. 22개 × 커넥션 풀 10 = 220개로 MySQL 기본 한도 151을 넘었다.
- 대응: 공용 어노테이션으로 컨텍스트를 13개에서 5개로, 이후 3개로 줄였다(공용 통합 · JPA · Kafka).
- 막는 장치: 컨텍스트 캐시 상한과 연결 한도 명시, `TestConnectionBudgetTest`, 컨텍스트 보고서의 "통합 필요"

### 2026-11-06 · 공유 컨테이너 스키마가 컨텍스트 종료 때 지워질 수 있음 (리뷰에서 발견, 사고 전)
- 증상(예상): 컨텍스트 하나가 닫히면 아직 살아 있는 다른 컨텍스트의 테이블이 사라진다.
- 원인: `ddl-auto: create-drop`은 컨텍스트가 닫힐 때 테이블을 DROP한다. 그런데 컨테이너 하나를 여러 컨텍스트가 같이 쓴다. Spring은 컨텍스트를 닫을 때 컨테이너도 `stop()`한다.
- 대응: `ddl-auto: create`로 바꿨다. 싱글턴 컨테이너는 `stop()`을 무시하고, 정리는 JVM이 끝날 때 Testcontainers ryuk가 한다.
- 막는 장치: 캐시 상한을 1로 두고 컨텍스트 5개가 축출되며 돌아도 통과하는 것을 확인했다.

### 2026-11-06 · 여러 작업이 전체 테스트를 동시에 돌려 기기가 마비됨
- 증상: 평소 45초인 전체 테스트가 7~13분 걸렸고, 컨텍스트 기동이 90초를 넘었다. 시간에 기대는 테스트가 흔들렸다.
- 원인: 여러 작업이 order-service 전체 테스트를 동시에 돌렸다. 실행마다 테스트 JVM(CPU 300% 안팎)과 MySQL 컨테이너가 따로 떠서 10코어 기기의 부하가 17~20이 됐다.
- 대응: 로컬에서는 바꾼 테스트와 컴파일만 돌리고, 모듈 전체 테스트는 PR CI가 돌린다(4장).

### 2026-11-06 · 기기가 바쁠 때 시간 단정이 깨짐
- 증상: 스케줄러 락 테스트가 부하 상황에서 "락이 아직 잡혀 있다"는 단정에서 실패했다. 단독으로 돌리면 통과했다.
- 원인: 실행부터 단정까지 걸린 시간이 락 최소 유지 시간(5초)을 넘었다. 같은 종류로 "3초 안에 끝난다" 단정과 `sleep(300)` 뒤 "기다리는 중일 것" 가정이 있었다.
- 대응: 저장된 시각의 차이(lock_until − locked_at ≥ 5초), 순서 단정, DB 상태 확인으로 바꿨다.
- 막는 장치: ArchUnit `TestWaitUsageTest`(sleep 금지), 3장 규칙

### 2026-11-06 · 컴파일 확인 없이 dev에 병합해 dev가 깨짐
- 증상: dev를 받은 뒤 테스트 컴파일이 실패했는데 그대로 dev에 병합됐다.
- 원인: 다른 작업이 요청 DTO 생성자를 바꿨는데, 병합 명령이 컴파일 실패에서 멈추지 않았다.
- 대응: 바로 고쳤다. 이후 병합 명령은 컴파일이 성공할 때만 이어지게 묶는다(4장).
