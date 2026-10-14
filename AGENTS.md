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
docs/spec/  도메인 스펙 (보호 경로). 엔티티·기능 작업 전에 읽는다
.github/    CI, PR 템플릿, 가드 스크립트
.claude/    권한·훅, 리뷰 에이전트, /adr, /commit
```

## 작업 규칙

### 권한 세 칸 (상세: docs/adr/0002)

- **혼자 끝까지**: 코드 구현·리팩터링, 테스트 작성, 브랜치·커밋·PR, ADR 선택지 정리(`/adr`, 상태 "제안"까지), 첫 배포 전 DB 마이그레이션
- **물어보고**: 의존성 추가, 빌드 설정, CI 설정, 공개 API 모양 변경, ADR 수락·변경, 스펙에 없는 도메인 규칙, 설계 선택지가 갈릴 때
- **절대 금지**: main에 머지/push, force push, `terraform apply`/`destroy`, 비밀키·`.env` 접근, 검사 규칙을 약하게 바꿔 통과(테스트 삭제·`@Disabled` 포함), 수락된 ADR 수정

보호 경로는 수정하지 않는다: `**/build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew`, `gradlew.bat`, `.github/`, `.claude/`, `AGENTS.md`, `CLAUDE.md`, `infra/`, `docs/spec/`, 수락된 ADR. 바꿔야 하면 PR "내가 결정할 것"에 무엇을 왜 바꿔야 하는지 적는다. 물어볼 사람이 없을 때(밤 작업)도 같다.

### 완료 정의

1. `./gradlew check` 통과. 작업을 끝내려 하면 Stop 훅이 `spotlessApply check`를 돌리고, 실패하면 한 번 막는다. 코드로 고칠 수 없는 원인(Docker 꺼짐 등)이면 고치려 하지 말고 PR "확인 못 한 것"과 사용자 보고에 실패 내용과 원인을 적고 끝낸다 (두 번째 종료 시도는 막히지 않는다)
2. 새 동작마다 그 동작을 검증하는 테스트
3. 리뷰 에이전트(`reviewer`)에 diff를 넘겨 검토받는다. 고친 지적은 건수만 쓰고, 고치지 않은 지적은 PR "내가 결정할 것"이나 "확인 못 한 것"에 쓴다
4. PR 본문은 `.github/pull_request_template.md`의 5항목을 채운다. "확인 못 한 것"은 비우지 않는다

### PR 본문

PR 본문은 머지하는 사람이 diff를 다 읽지 않고 머지 여부를 정하는 문서다. PR은 칼리 계정으로 공개 저장소에 남는다.

- 머지 판단에 필요한 것만 쓴다. diff, ADR, CI, 커밋에 있는 내용은 링크만 건다
- 작성자 시점으로 쓴다. 저장소 주인을 이름이나 3인칭으로 부르지 않는다
- 작업 과정(도구 차단, 명령 거부, 시행착오)은 PR이 아니라 작업 끝 보고에 쓴다
- 내부 번호나 약칭 대신 내용을 쓴다. 같은 내용은 한 섹션에만 쓰고, 굵게는 쓰지 않는다
- 문장은 "~합니다"로, 목록은 명사형으로 끝낸다

### 커밋

커밋은 롤백 단위로 나눈다. 절차는 `.claude/skills/commit/SKILL.md`

- 어느 커밋을 revert해도 나머지가 깨지지 않고, 커밋마다 `./gradlew check`가 통과한다
- 구조 변경(`refactor:`)과 동작 변경을 섞지 않는다. 구조 변경을 앞에 둔다
- 테스트는 검증하는 코드와 같은 커밋에 넣는다
- push 전에 정리한다 (force push가 막혀 있어 push한 커밋은 못 고친다)
- 메시지는 `유형: 한 문장`. 한 문장으로 안 써지면 더 쪼갠다
- PR "CI 밖 검증"에 커밋별 check 결과를 한 줄 적는다 (CI는 마지막 커밋만 검사한다)

### 그 밖의 규칙

- 검사 규칙이 틀렸다고 생각해도 고치지 않는다. PR에 이유를 적는다
- 버그는 재현 테스트를 먼저 쓰고(실패 확인) 고친다
- 브랜치 이름: `claude/하이픈-이름` (예: `claude/post-list-api`). main에서 바로 작업하지 않는다
- push는 `git push -u origin claude/…`처럼 브랜치 이름을 적는다. `git push`, `git push origin`, HEAD가 들어간 형태는 거부된다
- 머지는 사람이 한다 (docs/adr/0003)
- 계획·브레인스토밍 문서는 저장소에 두지 않는다. 확정된 결정만 ADR과 도메인 스펙(`docs/spec/`)에 둔다. 스펙을 바꿔야 하면 PR "내가 결정할 것"에 적는다

## 코드 규칙

- 포맷은 Spotless(palantir-java-format)가 정한다. 손으로 맞추지 않는다
- DB 접근 ([ADR 0012](docs/adr/0012-persistence-and-migration.md)): `infra`의 저장소 구현에서 `JdbcClient`로 SQL을 직접 쓴다. `domain`에는 영속성 애너테이션·Spring 의존을 두지 않는다
- 스키마는 Flyway 마이그레이션 파일(`app/src/main/resources/db/migration/V<번호>__<설명>.sql`)로만 바꾼다. 첫 배포 뒤에는 적용된 파일을 고치지 않고 새 버전을 더한다 (첫 배포 전은 ADR 0004)
- 테스트에서 DB가 필요하면 `kali.TestcontainersConfiguration`을 `@Import`한다. 메모리 DB(H2)로 대체하지 않는다
- 테스트 메서드 이름은 한글로 동작을 쓴다 (예: `DB가_연결되어_있으면_UP을_반환한다`)
- 패키지 구조 ([ADR 0011](docs/adr/0011-package-structure.md)): 기능별 패키지 `kali.<기능>` 안의 `web`, `application`, `domain`, `infra` 중 하나에 둔다. 루트에는 `KaliApplication`만, 여러 기능이 함께 쓰는 것은 `kali.common`에 둔다
  - 의존 방향: `web` → `application` → `domain`, `infra` → `application`·`domain`. 다른 기능의 `web`·`infra`에 의존하지 않고, 기능 사이에 순환이 없다. `kali.ArchitectureTest`가 검사한다
