---
name: commit
description: 커밋을 만들 때, 그리고 push 전에 커밋을 정리할 때 쓴다.
---

# 커밋 분할 기준

## 원칙

커밋은 **롤백 단위**로 나눈다. "이 커밋을 revert했을 때 나머지가 깨지지 않고 독립적으로 동작하는가"가 분할 기준이다.

여기에 네 가지를 더 지킨다.

1. **커밋마다 `./gradlew check`가 통과한다.** revert할 때뿐 아니라 각 커밋을 체크아웃했을 때도 돌아가야 한다. `git bisect`로 어느 지점을 찍어도 깨지지 않아야 원인 커밋을 찾을 수 있다. CI는 PR의 마지막 커밋만 검사하므로 중간 커밋은 직접 확인한다.
2. **구조 변경과 동작 변경을 섞지 않는다.** 구조 변경(이름 변경, 메서드 추출, 클래스 이동, 포맷팅)은 되돌리기 쉽고, 동작 변경은 되돌리기 어렵다. 성격이 다르니 커밋도 나눈다.
3. **테스트는 자기가 검증하는 코드와 같은 커밋에 넣는다.** 그래야 커밋 하나를 revert해도 테스트가 같이 빠져서 깨지지 않는다.
4. **push 전에 정리한다.** force push가 막혀 있어서 push한 커밋은 고칠 수 없다. 리뷰 에이전트 지적 반영까지 마치고 커밋을 정리한 다음 push한다. push한 뒤에 고칠 게 생기면 그 자체로 롤백 단위가 되는 커밋을 추가한다.

## 판단 절차

1. **구조 변경을 먼저 골라낸다.** 동작은 그대로인데 코드 모양만 바뀐 부분을 `refactor:` 커밋으로 묶어 맨 앞에 둔다
   - 예: `HealthController`에서 DB 확인 메서드를 별도 클래스로 추출함 → `refactor:` 커밋
   - 예외: 지역 변수 이름 하나 고친 정도의 작은 정리는 동작 변경 커밋에 같이 넣어도 된다
2. **남은 동작 변경으로 롤백 시나리오를 나열한다**
3. **시나리오별로 되돌려야 하는 파일/변경 범위를 묶는다**
4. **묶음 사이 의존이 있으면 기반 → 사용처 순서로 배치한다**
   - 예: 설정값 추가 커밋 → 그 설정값을 쓰는 로직 커밋
   - revert는 역순(사용처 먼저)으로 하면 깨지지 않는다
   - 커밋 본문에 "커밋 <짧은 SHA>에 의존"이라고 적어둔다
5. **각 커밋에 해당 테스트가 같이 들어갔는지 확인한다**
   - 동작과 상관없는 독립적인 테스트 수정(기존 테스트 정리 등)은 별도 커밋으로 먼저 둔다
6. **각 커밋 시점에서 `./gradlew check`가 통과하는지 확인한다**
   - 일괄 확인: `git rebase -x "./gradlew check" origin/main`
   - 결과는 PR "CI 밖 검증"에 커밋별로 한 줄 적는다
7. **커밋 메시지를 한 문장으로 써본다.** 한 문장으로 안 써지거나 "그리고"가 들어가면 더 쪼갤 신호다

## 커밋 메시지

```
유형: 한 문장

(필요하면 본문. 의존이 있으면 "커밋 <짧은 SHA>에 의존")
```

| 유형 | 쓰는 때 |
|---|---|
| `feat` | 새 동작 |
| `fix` | 잘못된 동작 수정 |
| `refactor` | 동작이 그대로인 구조 변경 |
| `test` | 동작 변경 없이 테스트만 추가·정리 |
| `docs` | 문서, ADR |
| `chore` | 빌드, CI, 하네스 설정 |

## 예시: health API 개선

### 변경 사항
- DB 확인 로직을 `HealthController`에서 `DatabaseHealthChecker`로 추출 (구조 변경)
- DB가 느릴 때 `/health`가 오래 걸리지 않도록 DB 조회에 2초 타임아웃 적용 (`DatabaseHealthChecker` + 타임아웃 테스트)
- `/health` 응답에 DB 확인에 걸린 시간 필드 추가 (`HealthController`에서 `DatabaseHealthChecker` 호출 전후로 측정, `HealthResponse` + 응답 필드 테스트)

### 롤백 시나리오

| 시나리오 | 되돌릴 범위 | 나머지 영향 |
|---|---|---|
| 타임아웃 2초가 짧아서 배포 직후 DB가 느릴 때 health가 계속 DOWN | 커밋 2 (타임아웃 + 타임아웃 테스트) | 커밋 1, 3은 독립적으로 동작 |
| 모니터링 도구가 응답의 새 필드를 처리하지 못함 | 커밋 3 (응답 필드 + 응답 필드 테스트) | 커밋 1, 2는 독립적으로 동작 |

### 분할 결과
- 커밋 1: `refactor: DB 확인 로직을 DatabaseHealthChecker로 추출`
- 커밋 2: `feat: health의 DB 조회에 2초 타임아웃 적용` (본문: 커밋 1에 의존)
- 커밋 3: `feat: health 응답에 DB 확인 시간 필드 추가` (본문: 커밋 1에 의존)

커밋 2와 3은 둘 다 커밋 1에 의존하지만 서로는 독립이라, 어느 쪽을 revert해도 다른 쪽은 그대로 동작한다. 시간 측정을 `DatabaseHealthChecker` 안에 넣으면 커밋 2와 같은 메서드를 고치게 되어 revert 때 충돌이 나므로, 측정은 호출하는 쪽(`HealthController`)에 둔다. 응답 필드 추가는 공개 API 모양 변경이라 "물어보고" 칸에 해당한다.

## 분할하지 않는 경우

- 변경이 모두 같은 이유로 롤백되는 경우 (분할하면 오히려 부분 revert 시 깨짐)
- 하나의 논리적 변경이 여러 파일에 걸쳐 있는 경우 (예: 메서드 이름 변경에 따른 호출부 일괄 수정)

의존 관계가 있다는 것만으로는 합치지 않는다. 기반 → 사용처 순서로 나누고, 각 커밋 시점에서 `./gradlew check`가 통과하면 분리한다.

## 참고: 롤백 대신 Expand-Contract

이 저장소는 별도 프론트가 없다(Thymeleaf 서버 렌더링). 그래서 이 방법은 **DB 스키마 변경**과 **이미 공개된 URL 변경**에 적용한다. 이 둘은 되돌리기 어렵거나(데이터), 바깥에서 이미 쓰고 있다(검색 엔진, 공유된 링크).

1. expand: 기존 것을 유지한 채 새 것을 추가한다 (예: 새 컬럼 추가, 새 URL 추가)
2. migrate: 사용처가 새 것을 쓰도록 바꾼다 (예: 코드가 새 컬럼을 읽고 씀, 옛 URL은 새 URL로 리다이렉트)
3. contract: 아무도 안 쓰게 된 기존 것을 제거한다 (예: 옛 컬럼 삭제. 공개된 URL의 리다이렉트는 남겨둘 수 있다)

단계마다 커밋(또는 배포)이 하나씩 나온다. 배포 순서를 맞춰야 하므로 상황에 따라 선택한다.

## 출처

- Linux 커널 Submitting patches, "Separate your changes": https://docs.kernel.org/process/submitting-patches.html
- Google eng-practices, Small CLs: https://google.github.io/eng-practices/review/developer/small-cls.html
- Kent Beck, Structure & Behavior: https://newsletter.kentbeck.com/p/structure-and-behavior
- Martin Fowler, Parallel Change: https://martinfowler.com/bliki/ParallelChange.html
