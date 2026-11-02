package kali.sync.domain;

/**
 * 동기화가 건너뛴 노트와 그 이유 (스펙 5. 발행의 문제 있는 노트).
 *
 * @param path 볼트 기준 노트 경로
 */
public record SkippedNote(String path, String reason) {}
