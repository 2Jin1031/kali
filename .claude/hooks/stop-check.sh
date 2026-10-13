#!/usr/bin/env bash
# Stop 훅: 작업을 끝내기 전에 포맷을 자동 수정하고 전체 검사를 돌린다.
# - 통과: exit 0
# - 실패 + 첫 종료 시도(stop_hook_active=false): exit 2 → 끝내지 못하고 아래 stderr를 받아 계속 고친다
# - 실패 + 이미 한 번 막힌 뒤(stop_hook_active=true): exit 0 → 무한 반복 방지 (Docker 꺼짐 등 코드로 못 고치는 원인)
# 바뀐 게 없으면 Gradle이 up-to-date로 건너뛰어 몇 초 안에 끝난다.
set -uo pipefail

# stdin JSON에서 stop_hook_active만 읽는다 (jq 없이)
input="$(cat 2>/dev/null || true)"
stop_hook_active=false
if grep -qE '"stop_hook_active"[[:space:]]*:[[:space:]]*true' <<<"$input"; then
  stop_hook_active=true
fi

if [[ -z "${CLAUDE_PROJECT_DIR:-}" ]] || ! cd "$CLAUDE_PROJECT_DIR"; then
  echo "Stop 훅: 프로젝트 폴더로 이동 실패 (CLAUDE_PROJECT_DIR='${CLAUDE_PROJECT_DIR:-}'). 검사를 못 돌림" >&2
  $stop_hook_active && { echo "검사 실패 상태로 종료 허용" >&2; exit 0; }
  echo "코드로 고칠 수 없는 원인이면 고치려 하지 말고, PR '확인 못 한 것'과 사용자 보고에 실패 내용과 원인을 적고 끝내라. 다음 종료 시도는 막지 않는다." >&2
  exit 2
fi

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

if ./gradlew spotlessApply check --console=plain >"$log" 2>&1; then
  exit 0
fi

if $stop_hook_active; then
  {
    echo "검사 실패 상태로 종료 허용 (stop_hook_active=true, 이미 한 번 막힘)"
    echo "--- 출력 마지막 30줄 ---"
    tail -n 30 "$log"
  } >&2
  exit 0
fi

{
  echo "./gradlew spotlessApply check 실패. 작업을 끝내기 전에 고쳐라."
  echo "검사 규칙을 약하게 바꾸지 말 것(테스트 삭제·@Disabled·설정 완화 금지). 규칙이 틀렸다고 보면 PR에 이유를 적는다."
  echo "코드로 고칠 수 없는 원인(Docker 꺼짐 등)이면 고치려 하지 말고, PR '확인 못 한 것'과 사용자 보고에 실패 내용과 원인을 적고 끝내라. 다음 종료 시도는 막지 않는다."
  echo "--- 출력 마지막 80줄 ---"
  tail -n 80 "$log"
} >&2
exit 2
