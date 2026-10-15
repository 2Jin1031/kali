#!/usr/bin/env bash
# guard.sh 시나리오 테스트: 임시 git 저장소에 위반 커밋을 만들고 guard.sh의 종료 코드와 출력을 확인한다.
# 시나리오는 깨보기 PR #10의 위반 2~6, 위반 없음, `사람-확인` 라벨 있음.
#
# 사용: guard-test.sh  (어디서 실행해도 됨. 실패한 시나리오가 있으면 exit 1)
set -euo pipefail

GUARD="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/guard.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git_() { git -C "$WORK" -c core.quotePath=false -c user.name=guard-test -c user.email=guard-test@example.com -c commit.gpgsign=false "$@"; }

# 기준 상태: 테스트 파일, 보호 경로, 수락된 ADR, 일반 코드
git_ init -q -b main
mkdir -p "$WORK/app/src/test/java/kali" "$WORK/app/src/main/java/kali" "$WORK/docs/adr"
cat >"$WORK/app/src/test/java/kali/SampleTest.java" <<'EOF'
class SampleTest {
    @Test
    void 통과한다() {}
}
EOF
echo 'plugins { java }' >"$WORK/app/build.gradle.kts"
printf '# 0001. 수락된 결정\n\n상태: 수락됨\n날짜: 2026-10-13\n' >"$WORK/docs/adr/0001-accepted.md"
echo 'class Sample {}' >"$WORK/app/src/main/java/kali/Sample.java"
git_ add -A
git_ commit -q -m base
BASE="$(git_ rev-parse HEAD)"

failures=0

# scenario <이름> <기대 종료 코드> <출력에 있어야 할 글자> <라벨 JSON> <변경 함수>
scenario() {
  local name="$1" expected_rc="$2" expected_text="$3" labels="$4" change="$5"
  git_ checkout -q -B case "$BASE"
  "$change"
  git_ add -A
  git_ commit -q -m "$name"
  local head output rc
  head="$(git_ rev-parse HEAD)"
  if output="$(cd "$WORK" && PR_LABELS="$labels" bash "$GUARD" "$BASE" "$head" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ "$rc" -eq "$expected_rc" ]] && grep -qF -- "$expected_text" <<<"$output"; then
    echo "통과: $name"
  else
    echo "실패: $name (기대: exit $expected_rc, '$expected_text' / 실제: exit $rc)"
    sed 's/^/    /' <<<"$output"
    failures=$((failures + 1))
  fi
}

no_violation() { echo 'class Sample { int x; }' >"$WORK/app/src/main/java/kali/Sample.java"; }
add_disabled() {
  local f="$WORK/app/src/test/java/kali/SampleTest.java"
  awk '/@Test/{print "    @Disabled"} {print}' "$f" >"$f.tmp" && mv "$f.tmp" "$f"
}
delete_test() { rm "$WORK/app/src/test/java/kali/SampleTest.java"; }
touch_build() { echo '// 주석' >>"$WORK/app/build.gradle.kts"; }
edit_accepted_adr() { echo '한 줄 추가' >>"$WORK/docs/adr/0001-accepted.md"; }
add_accepted_adr() { printf '# 0002. 새 결정\n\n상태: 수락됨\n' >"$WORK/docs/adr/0002-new.md"; }

scenario "위반 없음" 0 "가드 통과: 위반 없음" '[]' no_violation
scenario "위반 2: 테스트에 @Disabled 추가" 1 "::error::@Disabled 추가: app/src/test/java/kali/SampleTest.java" '[]' add_disabled
scenario "위반 3: 테스트 파일 삭제" 1 "::error::테스트 파일 삭제: app/src/test/java/kali/SampleTest.java" '[]' delete_test
scenario "위반 4: build.gradle.kts 변경" 1 "::error::보호 경로 변경: app/build.gradle.kts" '[]' touch_build
scenario "위반 5: 수락된 ADR 수정" 1 "::error::수락된 ADR 변경: docs/adr/0001-accepted.md" '[]' edit_accepted_adr
scenario "위반 6: 새 ADR을 수락됨으로 추가" 1 "::error::ADR 수락(사람 확인 필요): docs/adr/0002-new.md" '[]' add_accepted_adr
scenario "라벨 있음: 위반이 있어도 경고만 하고 통과" 0 "::warning::보호 경로 변경: app/build.gradle.kts" '["사람-확인"]' touch_build

if [[ "$failures" -gt 0 ]]; then
  echo "guard-test: 시나리오 ${failures}개 실패"
  exit 1
fi
echo "guard-test: 모든 시나리오 통과"
