# kali

칼리의 개인 블로그 겸 백엔드 포트폴리오 (Spring Boot + Thymeleaf + MySQL).

## 명령어

| 명령 | 용도 |
|---|---|
| `./gradlew check` | CI와 같은 전체 검사 (테스트 + Testcontainers MySQL + Spotless). PR 전에 반드시 통과 |
| `./gradlew spotlessApply` | 포맷 자동 수정 |
| `./gradlew bootTestRun` | 로컬 실행 (MySQL 컨테이너 자동 기동) |

Docker가 켜져 있어야 한다. JDK 25는 Gradle toolchain이 찾거나 내려받는다.

## 폴더 구조

```
app/        Spring Boot 앱 (패키지 루트 kali)
infra/      IaC (보호 경로, 비어 있음)
docs/adr/   확정된 결정. 작업 전 관련 ADR을 읽는다
.github/    CI, PR 템플릿, 가드 스크립트
.claude/    권한·훅, 리뷰 에이전트, /adr
```

## 작업 규칙

### 권한 세 칸 (상세: docs/adr/0002)

- **혼자 끝까지**: 코드 구현·리팩터링, 테스트 작성, 브랜치·커밋·PR, ADR 선택지 정리(`/adr`, 상태 "제안"까지), 첫 배포 전 DB 마이그레이션
- **물어보고**: 의존성 추가, 빌드 설정, CI 설정, 공개 API 모양 변경, ADR 수락·변경, 스펙에 없는 도메인 규칙, 설계 선택지가 갈릴 때
- **절대 금지**: main에 머지/push, force push, `terraform apply`/`destroy`, 비밀키·`.env` 접근, 검사 규칙을 약하게 바꿔 통과(테스트 삭제·`@Disabled` 포함), 수락된 ADR 수정

보호 경로는 수정하지 않는다: `**/build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `.github/`, `.claude/`, `AGENTS.md`, `CLAUDE.md`, `infra/`, 수락된 ADR. 바꿔야 하면 PR "내가 결정할 것"에 무엇을 왜 바꿔야 하는지 적는다. 물어볼 사람이 없을 때(밤 작업)도 같다.

### 완료 정의

1. `./gradlew check` 통과 (작업을 끝내려 하면 Stop 훅이 `spotlessApply check`를 돌리고, 실패하면 끝나지 않는다)
2. 새 동작마다 그 동작을 검증하는 테스트
3. 리뷰 에이전트(`reviewer`)에 diff를 넘겨 검토받고 결과를 PR에 넣는다
4. PR 본문은 `.github/pull_request_template.md` 6항목을 채운다. 검사 숫자는 정확한 커밋 SHA 기준. "확인 못 한 것"은 비우지 않는다

### 그 밖의 규칙

- 검사 규칙이 틀렸다고 생각해도 고치지 않는다. PR에 이유를 적는다
- 버그는 재현 테스트를 먼저 쓰고(실패 확인) 고친다
- 브랜치 이름: `claude/하이픈-이름` (예: `claude/post-list-api`). main에서 바로 작업하지 않는다
- 머지는 사람이 한다 (docs/adr/0003)
- 계획·브레인스토밍 문서는 저장소에 두지 않는다. 확정된 결정만 ADR로

## 코드 규칙

- 포맷은 Spotless(palantir-java-format)가 정한다. 손으로 맞추지 않는다
- 테스트에서 DB가 필요하면 `kali.TestcontainersConfiguration`을 `@Import`한다. 메모리 DB(H2)로 대체하지 않는다
- 테스트 메서드 이름은 한글로 동작을 쓴다 (예: `DB가_연결되어_있으면_UP을_반환한다`)
- 패키지 구조·레이어 규칙은 6A에서 정한다
