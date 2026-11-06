# CLAUDE.md

Claude Code가 이 저장소에서 작업할 때 따르는 안내서다. 사람이 읽어도 되도록 한국어로 쓴다.

## 프로젝트

CartMate 백엔드. 스포츠 팀 단위 공동구매(개인물품·공용물품) 커머스다.
지금은 **앱 하나(모놀리스)**지만 서버 분리(MSA)를 준비하는 중이라, 코드가 서비스 단위로 나뉘어 있고 서비스끼리는 정해진 방법으로만 통신한다.

- Java 25, Spring Boot 4.1.1, Gradle (멀티 모듈: 루트 앱 + `common`)
- Spring Web MVC, Data JPA, Security, Validation, Batch, Lombok
- MySQL 8.4, Redis(Redisson), QueryDSL 7.7 (OpenFeign 포크), springdoc-openapi
- 테스트: JUnit 5, Testcontainers(MySQL), ArchUnit

## 저장소 구조 (바뀔 수 있음 — 작업 전에 직접 확인)

구조는 서버 분리 작업으로 자주 바뀐다. **이 문서의 예시보다 실제 저장소를 믿는다.** 작업을 시작하면 먼저 아래로 지금 구조를 확인한다.

1. 모듈 목록: 아래 `settings.gradle` (이 파일은 세션마다 자동으로 읽힌다)
2. 서비스·모듈 위치: `git ls-files "*.java" | grep -oE "boundedContext/[^/]+/[^/]+" | sort -u`
3. 공통 코드·서비스 간 계약: `common` 모듈의 `global`, `shared` 패키지
4. 서비스 경계 규칙: `src/test/java/com/cartmate/cartmate/architecture/` 의 ArchUnit 테스트 (**규칙의 원본**)

@settings.gradle

확인한 구조가 이 문서와 다르면 작업 전에 사람에게 알리고, 이 문서를 고치자고 제안한다.

작성 시점(2026-11)의 모습은 대략 이렇다.

```
common/                       공통 라이브러리 모듈 (global: 예외·RsData·보안·이벤트·Outbox / shared: 서비스 간 계약)
src/main/java/com/cartmate/cartmate/boundedContext/
  <서비스>Service/<모듈>/     예: orderService/order, paymentService/payment
```

모듈 안은 같은 층으로 나눈다. 이 층 구조는 자주 바뀌지 않는다.

| 패키지 | 역할 |
|---|---|
| `in` | 컨트롤러, 이벤트 리스너·소비자, 스케줄러 |
| `app` | `XxxFacade`(트랜잭션 경계, 외부 진입점) + `XxxUseCase`(업무 로직, package-private) |
| `domain` | 엔티티, enum, 상태 전이 규칙 |
| `out` | 저장소, 다른 모듈·서버를 부르는 포트와 어댑터 |
| `dto`, `exception` | 요청·응답, `XxxErrorCode` |

## 명령어

Windows PowerShell 기준. Git Bash면 `./gradlew` 그대로.

```bash
./gradlew compileJava                                   # 컴파일만 (1분 안팎)
./gradlew test --tests "*CouponTest*"                   # 클래스 하나
./gradlew test --tests "*CouponTest*" --tests "*MemberCouponTest*"
./gradlew test                                          # 전체 (25분 이상 걸림)
```

- **전체 테스트는 사람이 요청할 때만 돌린다.** 작업 중엔 바꾼 코드와 관련된 테스트만 돌린다.
- 통합 테스트(`@SpringBootTest`)는 Testcontainers로 MySQL을 띄우므로 **Docker가 켜져 있어야** 한다.
- 결과 리포트: `build/reports/tests/test/index.html`

### 앱 실행

- 기본 프로필은 `dev`. DB·Redis 주소는 환경 변수로 받는다(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `REDIS_PASSWORD`, `REDIS_DATABASE`).
- 포트 8985. 팀 공용 개발 DB는 SSH 터널(13306)로 붙는다. 비밀번호는 저장소에 없다.
- 운영 설정 `application-prod.yaml`은 `INTERNAL_API_KEY` 등을 기본값 없이 요구한다.

## 서비스 경계 규칙 (ArchUnit 테스트로 검사됨)

`src/test/.../architecture`의 테스트가 아래를 깨면 실패한다. 아래는 요약이고 **테스트 코드가 원본**이다. 새 코드도 같은 원칙을 따른다.

1. **다른 모듈의 클래스를 직접 쓰지 않는다.** 다른 모듈과는 `common`의 `shared` 계약(이벤트·DTO·포트)이나, 내 모듈 `out`의 어댑터를 통해서만 만난다. 어댑터가 나중에 HTTP·Kafka 클라이언트로 바뀌는 유일한 자리다.
2. **다른 모듈의 테이블을 조인·외래 키·잠금으로 엮지 않는다.** 서비스마다 스키마가 나뉜다. 필요한 데이터는 이벤트로 받아 자기 복제본으로 가진다.
3. **다른 모듈에 쓰는 일은 커밋 후에.** 같은 트랜잭션 `@EventListener`로 다른 모듈 테이블을 바꾸지 않는다. 새 소비자는 Outbox 릴레이(`OutboxMessageRelayed`)를 `@TransactionalEventListener(fallbackExecution = true)`로 받는다.

### 이벤트·Outbox

- 서비스 간 이벤트는 `common/.../shared/<모듈>/event`에 두고 `DomainEvent`(`aggregateType`, `aggregateId`)를 구현한다.
- 발행은 `DomainEventPublisher.publish()`. 같은 트랜잭션에 `outbox_event` 행을 남긴다(활성 트랜잭션 필수).
- 토픽 이름은 패키지와 클래스 이름에서 자동으로 정해진다: `shared.order.event.OrderCanceledEvent` → `cartmate.order.order-canceled`. **토픽 이름은 서버 간 약속이라 이벤트 패키지·이름을 함부로 바꾸지 않는다.**
- 이벤트 단순 이름이 겹치면 앱이 시작되지 않는다(`DomainEventTypeRegistry`).
- 이벤트에 계좌번호 같은 민감 정보를 넣지 않는다. Outbox 행은 지우지 않고 남는다.
- 소비자는 같은 메시지가 두 번 와도 한 번만 처리되게 만든다(업무 키·유니크 제약 등).

### 동기 호출 (결제 서버)

- 주문 → 결제, 회원 → 결제 서버 호출은 포트(`OrderPaymentClient`, `MemberPaymentServerClient`)로만 한다.
- 설정 `cartmate.payment.client`로 구현을 고른다: `local`(기본, 같은 앱 Facade) / `http`(결제 내부 API `/internal/**`).
- 서버 간 HTTP는 `InternalApiClient`(시간 제한, `X-Internal-Api-Key`, RsData 해석, 연결 실패 → 503)를 쓴다.

## 코드 규칙

### 트랜잭션

- 트랜잭션 경계는 Facade에서만 정한다. UseCase에 `@Transactional`을 붙이지 않는다(예외: 한 건씩 따로 커밋해야 할 때 `TransactionTemplate`).
- **커밋 후 리스너에서 저장하려면 `REQUIRES_NEW`(또는 `@Async`)**. `REQUIRED`면 오류 없이 저장되지 않는다.
- 여러 건을 하나씩 처리하면서 실패를 건너뛰려면 건마다 트랜잭션을 나눈다. 저장소에서 예외가 나면 바깥 트랜잭션이 롤백 전용이 되어, 잡고 넘어가도 커밋 때 전부 롤백된다.

### 예외·응답

- 업무 오류는 `ServiceException(XxxErrorCode.YYY)`. 오류 코드는 모듈별 `XxxErrorCode` enum에.
- 넓은 예외(`DataIntegrityViolationException` 등)를 한 의미로 뭉뚱그리지 않는다. 중복 키는 MySQL 오류 코드 1062일 때만 "이미 있음"으로 바꾼다.
- 비정상 입력·이벤트를 조용히 무시하지 않는다(금액 0 이하, 없는 대상 등은 예외).
- 응답은 `RsData`. 컨트롤러 메서드에 `@Operation(operationId = ...)`를 고정한다(프론트 생성 코드의 훅 이름이 된다). 내부 API는 `@Hidden`.

### 도메인

- 상태 전이 규칙은 엔티티 안에 둔다(서비스에서 `if (status == ...)`로 흩어 놓지 않는다).
- 현재 시각은 밖에서 주입한다(`LocalDateTime now`를 인자로). 테스트에서 시각을 고정할 수 있게.
- 변경 감지에만 기대지 않고 필요한 곳은 명시적으로 저장한다.
- enum은 `description` 필드를 둔다.
- 할인·금액 계산 규칙은 한 곳(도메인)에만 둔다. 화면이 따로 계산하지 않도록 미리보기 API를 준다.

### DB

- 스키마는 `ddl-auto: update`. **새 테이블·컬럼 추가는 괜찮다.**
- **enum 컬럼에 값을 추가하면** Hibernate가 만든 CHECK 제약이 새 값을 막는다. 팀에 알리고 각 DB에서 CHECK를 지운다(`SHOW CREATE TABLE` → `ALTER TABLE ... DROP CHECK ...`).
- `datetime(6)`은 소수점 6자리까지라 `23:59:59.999999999`는 다음 날로 반올림된다. 기간 끝은 "다음 날 0시 전까지"로 표현한다.
- 날짜 범위로 자주 읽는 컬럼에는 인덱스를 둔다. 여러 개수는 한 쿼리(`GROUP BY`, `SUM(CASE WHEN)`)로 센다.

### 결제·보안

- 환불 중복 방지 키는 `RefundKey`에서만 만든다.
- 토큰에는 바뀌지 않는 식별자만(회원 ID, 역할, 판매자 ID). 판매자 ID는 주소가 아니라 토큰에서 꺼낸다.
- `/internal/**`는 서버 간 전용. 내부 API 키 필터가 확인한다.

### QueryDSL

- OpenFeign QueryDSL 7.7: `sum()` 대신 `sumLong()`.
- 커스텀 조회는 `XxxRepositoryCustom` + `XxxRepositoryImpl`.

## 테스트 작성

- 업무 로직은 **단위 테스트**(Mockito)로 먼저. 통합 테스트는 트랜잭션·동시성·여러 모듈 흐름처럼 꼭 필요할 때만.
- `@SpringBootTest`는 **설정이 조금만 달라도 스프링 컨텍스트를 새로 띄우고 캐시에 쌓는다**(properties, 내부 TestConfig, `@MockitoBean`, `webEnvironment`). 컨텍스트가 많아지면 테스트 JVM 메모리가 부족해지고(OutOfMemoryError) 전체 테스트가 느려진다. 새 통합 테스트는 기존 테스트와 같은 설정을 쓰고, 설정을 새로 만들어야 하면 이유를 주석으로 남긴다.
- 통합 테스트는 보통 `cartmate.scheduling.enabled=false`로 스케줄러를 끈다(다른 테스트 데이터를 건드리지 않게).
- 동시성 테스트는 `ExecutorService` + `CountDownLatch`로 실제로 동시에 부르고, DB 결과로 확인한다.

## Claude Code 작업 방식

- **큰 변경은 먼저 계획을 보여 주고 승인받은 뒤** 고친다. 특히 여러 모듈이나 다른 사람 코드를 건드릴 때.
- **테스트를 통과시키려고 테스트의 단언·시나리오·데이터를 바꾸지 않는다.** 테스트가 틀렸다고 판단되면 고치기 전에 이유를 설명하고 묻는다.
- 다른 모듈(다른 담당자) 코드를 바꿔야 하면 바꾸기 전에 알린다.
- 바꾼 뒤엔 관련 테스트를 돌려 결과를 보여 준다. 컴파일만 확인한 경우 그렇다고 말한다.
- 로컬 전용 설정(`application-dev.yaml`, IntelliJ 실행 설정, 비밀번호)은 커밋하지 않는다.
- 주석과 문서는 한국어로, 쉬운 말로 쓴다.

## 협업

- 통합 브랜치는 `dev`, 배포는 `main`. PR은 `dev`로.
- 브랜치: `feat/…`, `fix/…`, `refactor/…`, `test/…`, `docs/…`, `chore/…`
- 커밋: `type: 한글 설명` (예: `feat: 쿠폰 발급·쿠폰함`)
- PR 본문: 작업 내용 / 왜 / 변경 / DB 변경(enum 값 추가 시 CHECK 제약 안내) / 테스트 / 리뷰 요청
- 다른 담당자 코드를 바꾼 PR은 그 담당자를 리뷰어로 넣는다.
- **구조(모듈·패키지)를 바꾸는 PR은 이 문서도 같이 고친다.**