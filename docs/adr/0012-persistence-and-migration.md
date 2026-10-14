# 0012. DB 접근 방식과 마이그레이션 도구

상태: 수락됨
날짜: 2026-10-15

## 상황

다음 단계에서 글 엔티티와 테이블을 만든다. 그 전에 코드가 DB를 어떻게 읽고 쓰는지, 테이블을 무엇으로 바꾸는지 정해야 한다. 정하지 않으면 엔티티 PR마다 방식이 달라지고, 나중에 바꾸면 저장소 코드와 스키마를 함께 다시 써야 한다.

- 담아야 할 글 모델 ([도메인 스펙](../spec/domain.md) 3~6절): 옵시디언이 정한 UUID `id`(DB가 만들지 않음), 옛 slug 기록, 지금 slug와 옛 slug를 합쳐 모든 글에서 유일한 slug, `PUBLISHED`/`UNPUBLISHED` 상태, 내용 해시와 해시 방식, 바뀌지 않는 처음 발행 시각
- 글은 동기화가 옵시디언 내용으로 통째로 덮어쓴다 (1. 원본 위치). 글 사이 연관을 객체로 탐색할 일이 적고, 조회 화면은 목록·상세 몇 가지다 (10. 조회)
- 코드는 AI가 사람 없이(밤에) 쓴다. 실수가 SQL이나 테스트에 드러나야 아침에 diff로 잡을 수 있다
- [0011](0011-package-structure.md): 엔티티와 저장소 인터페이스는 `domain`, 저장소 구현은 `infra`. `domain`에 영속성 애너테이션을 쓸지는 엔티티 단계에서 정한다고 남겼다
- [0004](0004-db-migration-ownership.md): 마이그레이션 도구를 들이는 것은 의존성 추가라 사람에게 묻는다. 첫 배포 전까지 마이그레이션은 AI 혼자 바꾼다
- [0006](0006-database.md): MySQL 8. 테스트와 로컬 실행은 Testcontainers
- 지금 있는 의존성: `spring-boot-starter-jdbc` (JdbcTemplate, JdbcClient)
- Spring Boot 4.1.1이 관리하는 버전: Flyway 12.4.0, Liquibase 5.0.3, Hibernate 7.4.5.Final, Spring Data 2026.0.1. starter 이름은 `spring-boot-starter-flyway`, `spring-boot-starter-liquibase`, `spring-boot-starter-data-jdbc`, `spring-boot-starter-data-jpa`. MySQL용 Flyway 모듈 `org.flywaydb:flyway-mysql`도 같은 BOM이 관리한다 (Maven Central `spring-boot-dependencies-4.1.1.pom`, starter POM 확인, 2026-10-15)

## 선택지

DB 접근

- (가) JPA (Hibernate, `spring-boot-starter-data-jpa`)
- (나) Spring Data JDBC (`spring-boot-starter-data-jdbc`)
- (다) JdbcClient (이미 있는 `spring-boot-starter-jdbc`). SQL을 직접 쓰고 행을 도메인 객체로 직접 옮긴다

| 기준 | (가) JPA | (나) Spring Data JDBC | (다) JdbcClient |
|---|---|---|---|
| 정해진 UUID id | △ `save()`가 새 글인지 몰라 SELECT 후 INSERT (`merge`). `Persistable`로 피함 | ✕ id가 있으면 기존 글로 보고 UPDATE를 해 0행 갱신으로 실패. `Persistable`이나 `@Version` 필요 | ○ INSERT와 UPDATE를 직접 고름 |
| 옛 slug 기록 | ○ `@ElementCollection` | ○ 집합 안의 자식 컬렉션. 저장할 때마다 자식 행을 지우고 다시 넣음 | ○ 별도 테이블에 직접 씀 |
| 모든 글에서 slug 유일 | DB 유일 제약으로 같음. 충돌이 flush 시점에 나서 어느 줄에서 났는지 흐려짐 | DB 유일 제약으로 같음 | DB 유일 제약으로 같음. 충돌이 쓰는 줄에서 바로 남 |
| 상태, 내용 해시, 처음 발행 시각 | ○ | ○ | ○ |
| AI가 실수하기 쉬운 지점 | ✕ 지연 로딩과 N+1, 트랜잭션 밖 지연 로딩 예외, 변경 감지로 의도하지 않은 UPDATE, `equals`/`hashCode`. 실제 SQL이 diff에 보이지 않음 | △ 위 id 문제, 자식 컬렉션 통째 재작성. 나머지는 단순 | ○ 실행되는 SQL이 diff에 그대로 있음. 대신 매핑 코드를 손으로 써서 칸을 빠뜨릴 수 있음 (저장 후 다시 읽는 테스트로 잡음) |
| `domain`에 영속성 코드 | `@Entity` 등을 두거나, `infra`에 JPA 엔티티를 따로 두고 옮김 | `@Table`/`@Id`/`@MappedCollection`을 두거나 따로 둠 | 두지 않음. 매핑은 `infra`에만 |
| 코드 양 | 적음 | 적음 | 많음 (SQL, 행 매핑) |
| 한국 백엔드 면접관이 볼 때 | ○ 흔히 쓰고 영속성 컨텍스트·N+1을 묻는 기술 (공고 수는 이번에 세지 않음) | △ 덜 알려짐 | △ "왜 JPA가 아닌가"를 이 ADR로 답해야 함. SQL·인덱스·유일 제약을 직접 다룬 것은 보임 |
| 나중에 바꾸는 비용 | 높음 (도메인에 애너테이션을 두면 도메인까지 바뀜) | 중간 | 낮음 (도메인이 영속성을 모르므로 `infra`만 다시 씀. 스키마는 그대로) |

마이그레이션

- (A) Flyway (`spring-boot-starter-flyway` + `flyway-mysql`). 버전 번호가 붙은 SQL 파일을 순서대로 적용
- (B) Liquibase (`spring-boot-starter-liquibase`). XML/YAML/SQL 변경 목록, 되돌리기 정의
- (C) 도구 없이 `schema.sql` (Spring Boot SQL 초기화). 적용 기록이 없어 이미 있는 DB에 변경만 적용할 수 없음 → 배포 뒤에 쓸 수 없어 제외

| 기준 | (A) Flyway | (B) Liquibase |
|---|---|---|
| MySQL DDL을 그대로 씀 | ○ SQL 파일 | △ SQL 형식도 되지만 기본은 변경 목록 추상화 |
| 되돌리기 | ✕ 무료판에는 없는 것으로 앎 (12.4.0에서 확인 못 함). 새 버전 파일로 고침 (Expand-Contract, `.claude/skills/commit`) | ○ 변경마다 정의 |
| AI가 실수하기 쉬운 지점 | △ 적용된 파일을 고치면 체크섬 검증 실패 (테스트 DB는 매번 새로 떠서 CI가 못 잡음) | △ 같은 문제 + 변경 목록 형식 |
| Testcontainers에서 | ○ 앱이 뜰 때 자동 적용 | ○ 같음 |

추천: (다) JdbcClient + (A) Flyway. 스펙의 글은 동기화가 통째로 덮어쓰는 단순한 모양이라 JPA의 객체 탐색 이점이 적고, AI가 밤에 쓰는 코드에서는 실행되는 SQL이 diff에 보이는 쪽이 실수를 잡기 쉽다. 도메인이 영속성을 모르게 두면 JPA로 옮길 때 `infra`만 바뀐다.

## 결정

(다) JdbcClient + (A) Flyway.

- 저장소 인터페이스는 기능의 `domain`, 구현은 `infra`에 두고 `JdbcClient`로 SQL을 직접 쓴다 ([0011](0011-package-structure.md))
- `domain`에는 영속성 애너테이션과 Spring 의존을 두지 않는다. 행과 도메인 객체 사이 매핑은 `infra`에만 있다
- 스키마는 Flyway 마이그레이션 파일로만 바꾼다: `app/src/main/resources/db/migration/V<번호>__<설명>.sql`. JPA `ddl-auto`, `schema.sql`, 손으로 실행하는 DDL은 쓰지 않는다
- 첫 배포 전에는 머지된 마이그레이션 파일도 고치거나 합칠 수 있다 ([0004](0004-db-migration-ownership.md)). 첫 배포 뒤에는 적용된 파일을 고치지 않고 새 버전 파일을 더한다 (적용된 파일을 고치면 Flyway 체크섬 검증이 실패한다)
- Flyway는 앱이 뜰 때 자동으로 적용한다. 테스트도 같은 경로로 적용되므로 테스트용 스키마를 따로 만들지 않는다

## 결과

- `app/build.gradle.kts`에 `implementation("org.springframework.boot:spring-boot-starter-flyway")`, `implementation("org.flywaydb:flyway-mysql")`를 추가한다 (버전은 Spring Boot BOM)
- Testcontainers MySQL에서 Flyway가 실제로 도는지 테스트로 확인한다
- `AGENTS.md` "코드 규칙"에 DB 접근 방식과 마이그레이션 규칙을 한두 줄로 적는다
- 저장 후 다시 읽어 모든 칸을 비교하는 저장소 테스트를 저장소마다 둔다 (매핑 누락을 잡기 위해)
- 운영 DB에서 앱 시작 때 마이그레이션을 돌릴지, 배포 단계에서 따로 돌릴지는 첫 배포(6B) 때 다시 본다

## 재검토 조건

- 한 기능의 `infra`에서 행 매핑 코드가 SQL보다 커져 같은 실수(칸 누락)가 두 번 이상 리뷰나 테스트에 걸릴 때 (JPA나 Spring Data JDBC로 옮길지)
- 객체 사이를 따라가며 읽는 조회가 생겨 SQL 조인이 기능마다 반복될 때
- 첫 배포 때 앱 시작 마이그레이션이 배포 방식과 맞지 않을 때
