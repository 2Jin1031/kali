package kali.sync.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 볼트에서 읽은 노트 하나.
 *
 * @param path 볼트 기준 상대 경로 ({@code /}로 구분)
 * @param frontmatter YAML이 읽은 frontmatter. 날짜처럼 보이는 값도 글자 그대로 둔다 (스펙 6. 글 속성의 발행일). 없으면 빈 맵
 * @param body frontmatter를 뺀 본문. frontmatter를 읽지 못했으면 노트 전체 글자
 * @param frontmatterError frontmatter를 YAML로 읽지 못한 이유. 읽었으면 null
 */
public record Note(String path, Map<String, Object> frontmatter, String body, String frontmatterError) {

    public Note {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(body, "body");
        frontmatter = frontmatter == null ? Map.of() : frontmatter;
    }

    public static Note of(String path, Map<String, Object> frontmatter, String body) {
        return new Note(path, frontmatter, body, null);
    }

    public static Note unreadable(String path, String body, String error) {
        return new Note(path, Map.of(), body, Objects.requireNonNull(error, "error"));
    }

    public Optional<String> unreadableReason() {
        return Optional.ofNullable(frontmatterError);
    }

    /** frontmatter에 {@code publish: true}가 있는 노트만 발행 대상이다 (스펙 5. 발행). */
    public boolean isPublishTarget() {
        return Boolean.TRUE.equals(frontmatter.get("publish"));
    }
}
