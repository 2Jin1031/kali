# 0006. DB: MySQL 8

상태: 수락됨
날짜: 2026-10-13

## 상황

- 활동 로그에 JSON 칸이 있지만 JSON 안을 검색할 일은 적음 (조회는 type + 날짜)
- 태그(연결 테이블), 백링크 조회
- 이후 실험: Outbox 폴링(`FOR UPDATE SKIP LOCKED`), CDC(Debezium) 후보
- 한국어 글 검색은 P2
- 지원 공고 중 DB 이름을 적은 곳: MySQL 2곳, PostgreSQL 1곳

## 선택지

| 기준 | MySQL 8 | PostgreSQL |
|---|---|---|
| JSON 칸 | ○ JSON | ○ JSONB, JSON 안 검색은 더 강함 (거의 안 씀) |
| `SKIP LOCKED` | ○ 8.0부터 | ○ |
| Debezium | ○ binlog | ○ 논리적 복제 |
| 한국어 검색 | △ ngram 파서 | △ 확장 필요 |
| 공고·면접 연결 | ○ | △ |
| AWS RDS 비용·운영 | 같음 | 같음 |

## 결정

MySQL 8.

## 결과

- 테스트와 로컬 실행 모두 Testcontainers로 MySQL 8 컨테이너를 띄운다 (`./gradlew check`, `./gradlew bootTestRun`). worktree마다 DB가 따로 떠서 충돌이 없다

## 재검토 조건

- JSON 안을 조건으로 검색하는 기능이 생길 때
