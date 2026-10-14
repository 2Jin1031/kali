<!-- 목적: 머지하는 사람이 diff를 다 읽지 않고 머지 여부를 정할 수 있게 한다. 머지 판단에 필요 없거나 다른 곳(diff, ADR, CI, 커밋)에 이미 있는 내용은 쓰지 않는다 -->

## 한 일

<!-- 무엇을, 왜. 2~3문장, "~합니다". 결정 근거는 ADR 링크로 -->

## 내가 결정할 것

<!-- 사람이 정할 것을 "~할지" 형태로. 정해졌으면 "→ 결과". 없으면 "없음" -->

- 

## 확인 못 한 것

<!-- 확인하지 못한 동작과 이유, 고치지 않은 리뷰 지적. 작업 중 겪은 일은 쓰지 않는다. 비우지 않는다 -->

- 

## CI 밖 검증

<!-- CI가 보지 않는 검증 결과만 한 줄씩. CI 결과는 PR 체크에 보이므로 쓰지 않는다 -->

- 기준 커밋: `<짧은 SHA>`
- 리뷰 에이전트: 지적 N건 중 M건 수정
- 

## 전환 조건 체크

<!-- docs/adr/0003, 0004의 재검토 조건 진행 상황. PR을 만들 때와 push할 때마다 GitHub에서 다시 센다

AI 머지 전환 (docs/adr/0003)
- 세는 PR: main에 머지된 PR 중 `사람-확인` 라벨이 없는 것 (전환 후 AI가 머지하게 될 PR이 이 종류뿐). 아직 머지 전인 이번 PR은 세지 않는다
- `사람-확인` 라벨이 있는 PR은 세지 않고, 연속 기록을 끊지도 않는다
- 연속이 끊기는 경우: 세는 PR에 사람이 직접 고친 커밋(`Co-Authored-By: Claude` 트레일러가 없는 커밋)이 있을 때
- N/10 = 마지막으로 끊긴 PR 이후 세는 PR의 수

  1. 세는 PR 목록 (머지 순서)
     gh pr list --state merged --base main --limit 100 --json number,labels,mergedAt --jq '[.[] | select([.labels[].name] | index("사람-확인") | not)] | sort_by(.mergedAt) | map(.number)'
  2. PR마다 사람이 고친 커밋 수 (0이 아니면 연속이 끊김)
     gh pr view <번호> --json commits --jq '[.commits[] | select((.messageBody | test("Co-Authored-By: Claude"; "i")) | not)] | length'

실제 실패를 잡은 사례
- 세는 PR에서 CI `check`가 중간 커밋에서 실패했다가 뒤 커밋에서 고쳐진 경우, 또는 그 세션에서 Stop 훅이 실패를 막은 경우
- 직전 머지 PR 본문의 사례 수에 이번 PR에서 새로 생긴 사례를 더한다

  3. 직전 머지 PR 본문의 이 섹션
     gh pr list --state merged --base main --limit 100 --json number,mergedAt,body --jq 'sort_by(.mergedAt) | last | "#\(.number)\n" + (.body | split("## 전환 조건 체크")[1])'
  4. 이번 PR 커밋별 CI `check` 결과 (커밋 순서. failure 뒤에 success가 있으면 사례. CI는 push마다 마지막 커밋만 검사하므로 결과가 없는 커밋도 있다)
     for s in $(gh pr view <번호> --json commits --jq '.commits[].oid'); do gh api "repos/{owner}/{repo}/commits/$s/check-runs" --jq ".check_runs[] | select(.name==\"check\") | \"${s:0:7} \(.conclusion)\""; done
-->

- AI 머지 전환: N/10, 실제 실패를 잡은 사례 N건
- DB 마이그레이션 "물어보고"로 이동: 해당 없음
