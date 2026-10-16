# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 현황

CartMate 백엔드. 막 생성된 Spring Boot 프로젝트로, 현재는 `CartmateApplication`과 `contextLoads` 테스트만 있다. 도메인 코드, 패키지 계층 구조, 데이터소스 설정은 아직 없다. 첫 기능을 추가할 때는 기존 컨벤션이 있다고 가정하지 말고 의도적으로 컨벤션을 정해야 한다.

## 기술 스택

- Java 25 (Gradle toolchain), Spring Boot 4.1.1, Gradle wrapper
- Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation, Lombok, DevTools
- MySQL (`mysql-connector-j`, runtime)
- 기본 패키지: `com.cartmate.cartmate`, 설정 파일: `src/main/resources/application.yaml`

참고: Spring Boot 4는 테스트 스타터가 모듈별로 분리되어 있다(`spring-boot-starter-webmvc-test`, `-data-jpa-test` 등). 의존성을 추가할 때도 이 패턴을 따른다.

## 명령어

Windows에서는 `gradlew.bat`을 사용한다(Git Bash에서는 `./gradlew`).

```bash
./gradlew build                     # 컴파일 + 테스트
./gradlew bootRun                   # 애플리케이션 실행
./gradlew test                      # 전체 테스트
./gradlew test --tests "com.cartmate.cartmate.CartmateApplicationTests"            # 단일 클래스
./gradlew test --tests "com.cartmate.cartmate.CartmateApplicationTests.contextLoads" # 단일 메서드
```

기본 활성 프로필은 `local`(`application.yaml`)이며, `application-local.yaml`이 로컬 MySQL(`localhost:3306/cartmate`, 계정 `root`)에 연결한다. `application-local.yaml`은 `.gitignore` 대상이라 저장소에 없으므로 각자 로컬에 만들어야 한다. 테스트 전용 DB 설정은 아직 없어서 `@SpringBootTest`도 로컬 MySQL이 떠 있어야 통과한다. 또한 Spring Security가 classpath에 있으므로 `SecurityFilterChain`을 정의하기 전까지는 모든 엔드포인트가 기본적으로 인증을 요구한다.

## 협업 컨벤션

- 기본/통합 브랜치는 `dev`이며 PR은 `dev`로 보낸다. `main` 브랜치도 존재한다. 작업 브랜치는 `chore/initial-setup` 같은 형식을 사용한다.
- 커밋 메시지: `type: 설명` 형식, 설명은 한글 (예: `feat: 초기설정`, `chore: PR 템플릿 추가`).
- 이슈는 `.github/ISSUE_TEMPLATE`을 사용한다(제목 접두사 `[Feat]`, `[Refactor]`). PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md`(관련 이슈 / 작업 내용 / 테스트 / 리뷰 요청 사항 / 체크리스트)를 따르며, 이슈는 `Closes #N`으로 연결한다.
