#!/usr/bin/env bash
# 가드: PR의 base 대비 diff에서 "사람 판단이 필요한 변경"을 찾는다.
#   1. 테스트 코드에 @Disabled 추가
#   2. 테스트 파일 삭제
#   3. 보호 경로 변경
#   4. docs/adr/ 의 "상태: 수락됨" ADR 수정·삭제
# PR에 `사람-확인` 라벨이 있으면 경고만 하고 통과한다.
#
# 사용: guard.sh <base-sha> <head-sha>
# 환경변수: PR_LABELS = 라벨 이름 JSON 배열 (예: ["사람-확인"])
set -euo pipefail

BASE="${1:?base sha 필요}"
HEAD="${2:?head sha 필요}"
LABELS="${PR_LABELS:-[]}"
APPROVAL_LABEL="사람-확인"

violations=()

is_test_path() { [[ "$1" == */src/test/* || "$1" == src/test/* ]]; }

is_protected_path() {
  local p="$1"
  [[ "$p" == build.gradle.kts || "$p" == */build.gradle.kts ]] && return 0
  [[ "$p" == settings.gradle.kts ]] && return 0
  [[ "$p" == gradle/* ]] && return 0
  [[ "$p" == .github/* ]] && return 0
  [[ "$p" == .claude/* ]] && return 0
  [[ "$p" == AGENTS.md || "$p" == CLAUDE.md ]] && return 0
  [[ "$p" == infra/* ]] && return 0
  return 1
}

is_accepted_adr_at_base() {
  local p="$1"
  [[ "$p" == docs/adr/* ]] || return 1
  git show "$BASE:$p" 2>/dev/null | grep -q "상태: 수락됨"
}

# 1. 테스트 코드에 @Disabled 추가 (@DisabledIf..., @DisabledOn... 포함)
while IFS= read -r line; do
  violations+=("@Disabled 추가: $line")
done < <(
  git diff --unified=0 "$BASE...$HEAD" -- '*/src/test/*' 'src/test/*' \
    | awk '/^\+\+\+ /{file=substr($0,7); next} /^\+.*@Disabled/{print file ": " substr($0,2)}'
)

# 2~4. 파일 단위 검사
while IFS=$'\t' read -r status path1 path2; do
  case "$status" in
    D)
      is_test_path "$path1" && violations+=("테스트 파일 삭제: $path1")
      ;;
    R*)
      if is_test_path "$path1" && ! is_test_path "$path2"; then
        violations+=("테스트 파일 삭제(테스트 폴더 밖으로 이동): $path1 -> $path2")
      fi
      ;;
  esac

  for p in "$path1" ${path2:+"$path2"}; do
    is_protected_path "$p" && violations+=("보호 경로 변경: $p ($status)")
  done

  if [[ "$status" != A ]] && is_accepted_adr_at_base "$path1"; then
    violations+=("수락된 ADR 변경: $path1 ($status)")
  fi
done < <(git diff --name-status --find-renames "$BASE...$HEAD")

if [[ ${#violations[@]} -eq 0 ]]; then
  echo "가드 통과: 위반 없음"
  exit 0
fi

approved=false
if grep -q "\"$APPROVAL_LABEL\"" <<<"$LABELS"; then
  approved=true
fi

level=error
$approved && level=warning

echo "가드: 위반 ${#violations[@]}건"
for v in "${violations[@]}"; do
  echo "::$level::$v"
done

if $approved; then
  echo "\`$APPROVAL_LABEL\` 라벨이 있어 경고만 하고 통과"
  exit 0
fi

echo "사람이 확인했다면 PR에 \`$APPROVAL_LABEL\` 라벨을 달고 다시 실행"
exit 1
