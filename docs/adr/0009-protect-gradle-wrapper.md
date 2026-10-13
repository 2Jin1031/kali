# 0009. Gradle wrapper(gradlew)를 보호 경로에 추가

상태: 수락됨
날짜: 2026-10-14
0002 보완 (0002는 그대로 두고 보호 경로 목록에 더한다)

## 상황

CI의 `check`는 `./gradlew check`를 실행한다. `gradlew`, `gradlew.bat`은 0002의 보호 경로 목록에 없다. PR이 `gradlew`를 `exit 0`으로 바꾸면 `check`가 아무것도 검사하지 않고 초록이 된다. wrapper 변경은 원래 "빌드 설정"이라 "물어보고" 칸에 속한다.

## 선택지

- (가) `gradlew`, `gradlew.bat`을 보호 경로에 추가
- (나) 그대로 두고 리뷰 에이전트와 사람이 diff에서 확인

| 기준 | (가) | (나) |
|---|---|---|
| `check` 무력화를 기계적으로 막음 | ○ CI 가드 + 권한 deny | ✕ 사람이 놓치면 통과 |
| wrapper 업그레이드 비용 | 사람이 `사람-확인` 라벨로 인정 | 없음 |
| 0002의 "빌드 설정 = 물어보고"와 일치 | ○ | △ |

## 결정

(가). 보호 경로에 `gradlew`, `gradlew.bat`을 추가한다 (`gradle/`은 0002에 이미 있다).

## 결과

- CI 가드(`.github/scripts/guard.sh`)가 두 파일 변경을 위반으로 처리한다
- `.claude/settings.json` deny에 `Edit(/gradlew)`, `Edit(/gradlew.bat)`
- `AGENTS.md` 보호 경로 목록에 추가
- Gradle 버전 업그레이드는 사람에게 묻고, PR에 `사람-확인` 라벨이 필요하다

## 재검토 조건

- `check`를 wrapper 없이 실행하는 방식(예: CI에서 setup-gradle이 고정 버전으로 실행)으로 바꿀 때
