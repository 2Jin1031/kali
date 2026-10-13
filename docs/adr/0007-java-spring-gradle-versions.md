# 0007. Java 25 / Spring Boot 4.1 / Gradle 9 + Kotlin DSL

상태: 수락됨
날짜: 2026-10-13

## 상황 (2026-10-13 기준)

- Spring Boot 4.1.x가 최신 (4.1.0 2026-06, 4.1.1 2026-08). 4.0은 2026-12에 무료 지원 종료, 4.1은 2027-07까지
- Spring Boot 4.1은 Java 17~26 호환. Java 25는 2025-09 나온 LTS
- 새 프로젝트라 옮길 코드가 없음

## 선택지

| 항목 | 고른 것 | 다른 선택지 | 이유 |
|---|---|---|---|
| Java | 25 (LTS) | 21 (이전 LTS) | JEP 491로 가상 스레드 + `synchronized` pinning 해결. Spring Boot 4가 정식 지원 |
| Spring Boot | 4.1.x | 4.0.x | 4.0은 두 달 뒤 지원 종료 → 업그레이드를 두 번 하게 됨 |
| Gradle | 9.x + Kotlin DSL | Groovy DSL | 자동완성·타입 검사로 설정 오타를 빌드 전에 잡음 |

| 기준 | Java 21 | Java 25 |
|---|---|---|
| 가상 스레드 pinning | `synchronized`에서 발생 | 해결 (네이티브 호출은 여전히) |
| 도구 호환 | 가장 안정 | 대부분 따라옴, 일부는 최신 버전 필요 |

## 결정

Java 25 (배포 JDK는 Corretto) / Spring Boot 4.1.x / Gradle 9.x + Kotlin DSL.

## 결과

- Gradle toolchain으로 Java 25를 고정한다. 필요하면 21 toolchain으로 pinning 비교 실험을 할 수 있다
- CI는 Corretto 25

## 재검토 조건

- 쓰려는 라이브러리가 Spring Boot 4 / Java 25를 아직 지원하지 않을 때
- Spring Boot 4.1 무료 지원 종료(2027-07) 전에 다음 버전으로
