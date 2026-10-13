## 1. 한 일 (무엇을, 왜)

-

## 2. 돌린 검사와 숫자

- 기준 커밋: `<전체 SHA>`
- `./gradlew check`: 통과 / 실패
- 테스트: N passed, N failed, N skipped
- CI: <!-- 링크 또는 "올린 뒤 확인" -->

## 3. 리뷰 에이전트 결과

<!-- .claude/agents/reviewer.md 결과를 그대로 붙인다 -->

## 4. 확인 못 한 것

-

## 5. 내가 결정할 것

<!-- 보호 경로 변경, 의존성 추가, ADR 수락처럼 사람이 정할 것. 없으면 "없음" -->

-

## 6. 전환 조건 체크

<!-- docs/adr/0003-merge-authority.md, 0004-db-migration-ownership.md 의 재검토 조건 중 이번 PR로 충족된 것 -->

- [ ] AI 머지 전환: ① AI PR 10개 연속 수정 없이 머지 (현재 N/10) ② Stop 훅이나 CI가 실제로 실패를 잡은 적 1번 이상
- [ ] DB 마이그레이션 "물어보고"로 이동: 첫 배포 또는 실제 데이터가 DB에 들어가기 시작
