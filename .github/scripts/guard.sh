#!/usr/bin/env bash
# 가드: PR의 base 대비 diff에서 "사람 판단이 필요한 변경"을 찾는다.
#   1. 테스트 코드에 @Disabled 추가
#   2. 테스트 파일 삭제
#   3. 보호 경로 변경: **/build.gradle.kts, settings.gradle.kts, gradle/, gradlew, gradlew.bat,
#      .github/, .claude/, AGENTS.md, CLAUDE.md, infra/, docs/spec/
#   4. docs/adr/ 의 "상태: 수락됨" ADR 수정·삭제
#   5. ADR 수락 (추가된 줄에 "상태: 수락됨". 수락은 사람만 한다)
# PR에 `사람-확인` 라벨이 있으면 경고만 하고 통과한다.
# git 명령이 실패하면 통과시키지 않고 실패한다 (fail-closed).
#
# 사용: guard.sh <base-sha> <head-sha>
# 환경변수: PR_LABELS = 라벨 이름 JSON 배열 (예: ["사람-확인"])
set -euo pipefail

BASE="${1:?base sha 필요}"
HEAD="${2:?head sha 필요}"
LABELS="${PR_LABELS:-[]}"
APPROVAL_LABEL="사람-확인"

# 한글 경로를 따옴표·8진수 이스케이프 없이 그대로 출력
git_() { git -c core.quotePath=false "$@"; }

for sha in "$BASE" "$HEAD"; do
  if ! git_ cat-file -e "$sha^{commit}" 2>/dev/null; then
    echo "::error::커밋을 찾을 수 없음: $sha (fetch 범위 확인)"
    exit 1
  fi
done
git_ merge-base "$BASE" "$HEAD" >/dev/null || { echo "::error::merge-base 없음: $BASE $HEAD"; exit 1; }

# 명령 치환은 실패하면 set -e로 스크립트가 멈춘다 (빈 입력으로 통과하지 않게)
name_status="$(git_ diff --name-status --find-renames "$BASE...$HEAD")"
added_disabled="$(
  git_ diff --unified=0 "$BASE...$HEAD" \
    | awk '/^\+\+\+ /{file=substr($0,7); next}
           /^\+.*@[[:space:]]*([A-Za-z_][A-Za-z0-9_]*[[:space:]]*\.[[:space:]]*)*Disabled/{print file "\t" substr($0,2)}'
)"
# 줄 전체가 상태 표기인 경우만 (README 표의 "| 0009 | … | 수락됨 |" 같은 줄은 제외)
added_acceptance="$(
  git_ diff --unified=0 "$BASE...$HEAD" -- docs/adr/ \
    | awk '/^\+\+\+ /{file=substr($0,7); next}
           /^\+상태:[[:space:]]*수락됨[[:space:]]*$/{print file}' \
    | sort -u
)"

violations=()

# src/test, src/testFixtures, src/integrationTest 등
is_test_path() { [[ "$1" =~ (^|/)src/[^/]*[Tt]est[^/]*/ ]]; }

is_protected_path() {
  local p="$1"
  [[ "$p" == build.gradle.kts || "$p" == */build.gradle.kts ]] && return 0
  [[ "$p" == settings.gradle.kts ]] && return 0
  [[ "$p" == gradlew || "$p" == gradlew.bat ]] && return 0
  [[ "$p" == gradle/* ]] && return 0
  [[ "$p" == .github/* ]] && return 0
  [[ "$p" == .claude/* ]] && return 0
  [[ "$p" == AGENTS.md || "$p" == CLAUDE.md ]] && return 0
  [[ "$p" == infra/* ]] && return 0
  [[ "$p" == docs/spec/* ]] && return 0
  return 1
}

is_accepted_adr_at_base() {
  local p="$1" content
  [[ "$p" == docs/adr/* ]] || return 1
  content="$(git_ show "$BASE:$p" 2>/dev/null)" || return 1
  # 본문에 같은 글자가 있어도 안 되게, 줄 전체가 상태 표기인 경우만
  grep -qE '^상태:[[:space:]]*수락됨[[:space:]]*$' <<<"$content"
}

# 1. 테스트 코드에 @Disabled 추가 (@DisabledIf..., 정규화된 이름 포함)
while IFS=$'\t' read -r file line; do
  [[ -z "$file" ]] && continue
  is_test_path "$file" && violations+=("@Disabled 추가: $file: $line")
done <<<"$added_disabled"

# 5. ADR 수락 (새 파일을 수락됨으로 추가, 제안 → 수락됨 변경 모두)
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  violations+=("ADR 수락(사람 확인 필요): $file")
done <<<"$added_acceptance"

# 2~4. 파일 단위 검사
while IFS=$'\t' read -r status path1 path2; do
  [[ -z "$status" ]] && continue
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
done <<<"$name_status"

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
