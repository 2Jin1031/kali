# 0011. 패키지 구조와 의존 방향

상태: 수락됨
날짜: 2026-10-14
깨보기 5: 수락된 ADR 수정

## 상황

다음 단계부터 엔티티와 기능을 AI가 사람 없이(밤에) 만든다. 새 코드를 어느 패키지에 둘지, 어느 쪽으로 의존할지 정해져 있지 않으면 밤마다 다른 자리에 두게 되고, 아침에 사람이 diff에서 그것까지 봐야 한다. 규칙은 테스트로 강제해 `./gradlew check`에서 걸리게 한다.

- 다룰 영역 ([도메인 스펙](../spec/domain.md)): 글, 링크(본문에서 다시 만드는 파생 데이터), 히스토리, 동기화(옵시디언 볼트를 읽어 글·링크를 맞춤), 조회 화면(홈, 글 목록, 글 상세. 글·링크·히스토리를 함께 읽음)
- 기능 사이 반응(캐시 무효화, 백링크 갱신)은 스펙 11. 이벤트로 잇는다. Outbox, 캐싱, MQ는 나중에 붙인다
- 화면은 Thymeleaf 서버 렌더링 하나다 ([0005](0005-view-technology.md))
- 지금 코드는 `kali.KaliApplication`과 `kali.health.HealthController`뿐이다
- 검사 도구: ArchUnit `com.tngtech.archunit:archunit` 1.5.1. Maven Central `maven-metadata.xml`의 최신 안정 버전 (2026-10-14 확인)
- 스펙에서 미정인 이름(히스토리의 코드 이름 등)은 여기서 정하지 않는다

## 선택지

- (가) 레이어별 패키지: `kali.web`, `kali.application`, `kali.domain`, `kali.infra` 아래에 기능별 클래스
- (나) 기능별 패키지 + 기능 안 레이어 네 개: `kali.<기능>.web`, `.application`, `.domain`, `.infra`
- (다) 기능별 헥사고날: `kali.<기능>.domain`, `.application.port.in`, `.application.port.out`, `.adapter.in.web`, `.adapter.out.persistence` 등

| 기준 | (가) 레이어별 | (나) 기능별 + 레이어 | (다) 기능별 헥사고날 |
|---|---|---|---|
| 처음 보는 사람이 폴더만 보고 기능을 찾음 | ✕ 한 기능이 네 폴더에 흩어짐 | ○ 최상위 폴더가 기능 이름 | ○ 최상위는 같음. 안쪽 깊이가 깊음 |
| AI가 새 코드를 둘 자리를 헷갈리지 않음 | △ 레이어는 분명, 기능 경계가 없음 | ○ 기능 하나 + 레이어 네 개 중 하나 | △ 포트·어댑터 방향과 in/out을 매번 골라야 함 |
| ArchUnit으로 검사 | ○ 레이어 규칙 | ○ 레이어 규칙 + 기능 사이 규칙 | ○ `onionArchitecture` 등 |
| Outbox, 캐싱, MQ를 붙일 자리 | △ `infra` 하나에 모든 기능의 어댑터가 쌓임 | ○ 각 기능의 `infra`, 여러 기능이 쓰면 `kali.common` | ○ 각 기능의 `adapter.out` |
| 클래스 수 (지금 규모: 기능 6개 안팎) | ○ 적음 | ○ 적음 | ✕ 포트 인터페이스가 기능마다 늘어남 |
| 나중에 바꾸는 비용 | 중간 (기능별로 나누려면 모든 클래스 이동) | 낮음 ((다)로 가려면 `infra` → `adapter`, 필요한 곳만 포트 추가) | 중간 (줄이려면 포트 제거와 이동) |

추천: (나). 폴더가 곧 기능 목록이라 면접관과 AI 모두 자리를 바로 찾고, 규칙이 "기능 + 레이어 네 개"로 단순해 ArchUnit으로 그대로 옮겨진다. 포트가 필요한 곳(외부 수집, MQ)만 `application`에 인터페이스를 두면 (다)의 이점도 얻는다.

## 결정

(나). 기능별 패키지 안에 레이어 네 개를 둔다.

패키지

- `kali` 바로 아래에는 `KaliApplication`만 둔다
- `kali.<기능>`: 글, 링크, 히스토리, 동기화, 조회 화면, health가 각각 기능 하나다. 기능 이름은 스펙 용어를 따르고, 스펙에서 미정인 이름은 스펙이 정한 뒤에 쓴다
- 기능 안의 클래스는 모두 다음 네 레이어 중 하나에 둔다. 기능 패키지 바로 아래에는 두지 않는다. 쓰지 않는 레이어는 만들지 않는다

| 레이어 | 두는 것 |
|---|---|
| `web` | 컨트롤러, 화면 모델, 요청·응답 형식 |
| `application` | 유스케이스(서비스), 트랜잭션 경계, 이벤트 리스너, 외부로 나가는 인터페이스(포트) |
| `domain` | 엔티티, 값 객체, 도메인 규칙, 도메인 이벤트, 저장소 인터페이스 |
| `infra` | 저장소 구현, 외부 시스템 클라이언트(GitHub, 볼트 읽기, 이미지 저장소), Outbox·MQ 어댑터 |

- `kali.common`: 둘 이상의 기능이 함께 쓰는 것만 둔다 (설정, Outbox 공통 처리 등). 레이어 규칙을 따르지 않지만 기능에 의존하지 않는다
- 조회 화면은 글·링크·히스토리를 함께 읽는 별도 기능이다. 글 기능이 링크를 읽고 링크 기능이 글 이벤트를 받는 순환을 막기 위해서다

의존 방향 (화살표는 의존해도 되는 쪽)

- `web` → `application`, `domain`
- `application` → `domain`
- `infra` → `application`, `domain`
- `domain` → `domain`만 (다른 기능의 `domain` 포함)
- 모든 레이어 → `kali.common`
- 같은 레이어끼리는 기능이 달라도 의존할 수 있다. 다만 다른 기능의 `web`과 `infra`에는 의존하지 않는다
- `application`이 외부 시스템을 불러야 하면 `application`에 인터페이스를 두고 `infra`가 구현한다
- 기능 사이에 순환 의존이 없어야 한다 (예: 동기화 → 글, 링크 → 글. 글 → 링크는 안 됨)
- 기능 사이 반응(캐시 무효화, 백링크 갱신)은 직접 호출 대신 스펙 11. 이벤트를 받아서 한다. 이벤트 클래스는 내는 기능의 `domain`에 둔다
- 프레임워크(Spring, JPA 등) 의존은 이 ADR에서 제한하지 않는다. `domain`에 영속성 애너테이션을 쓸지는 엔티티 단계에서 정한다

## 결과

- `kali.health.HealthController`를 `kali.health.web`으로 옮긴다 (URL `/health`는 그대로)
- 위 규칙을 `app/src/test/java/kali/ArchitectureTest.java`의 ArchUnit 테스트로 강제한다. 아직 클래스가 없는 레이어·기능 때문에 실패하지 않고, 클래스가 생기면 바로 적용된다
- `app/build.gradle.kts`에 `testImplementation("com.tngtech.archunit:archunit:1.5.1")`을 추가한다
- `AGENTS.md` "코드 규칙"의 패키지 구조 줄을 이 ADR 요약과 링크로 바꾼다
- 이 ADR을 고치지 않는다. 구조를 바꾸려면 새 ADR을 제안한다

## 재검토 조건

- 한 기능의 `application`이나 `infra`에 클래스가 20개를 넘어 기능을 다시 나눠야 할 때
- 포트 인터페이스를 두는 기능이 절반을 넘을 때 ((다)로 옮길지)
- ArchUnit 규칙을 피하려고 `kali.common`에 기능 코드가 쌓일 때
