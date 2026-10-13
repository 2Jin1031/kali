#!/usr/bin/env bash
# Stop 훅: 작업을 끝내기 전에 포맷을 자동 수정하고 전체 검사를 돌린다.
# 실패하면 exit 2 → Claude Code가 작업을 끝내지 못하고, 아래 stderr를 받아 계속 고친다.
# 바뀐 게 없으면 Gradle이 up-to-date로 건너뛰어 몇 초 안에 끝난다.
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:?}" || exit 2

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

if ./gradlew spotlessApply check --console=plain >"$log" 2>&1; then
  exit 0
fi

{
  echo "./gradlew spotlessApply check 실패. 작업을 끝내기 전에 고쳐라."
  echo "검사 규칙을 약하게 바꾸지 말 것(테스트 삭제·@Disabled·설정 완화 금지). 규칙이 틀렸다고 보면 PR에 이유를 적는다."
  echo "Docker가 꺼져 있는 등 코드로 고칠 수 없는 원인이면 그 사실을 사용자에게 보고한다."
  echo "--- 출력 마지막 80줄 ---"
  tail -n 80 "$log"
} >&2
exit 2
