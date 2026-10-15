package kali.post.domain;

import java.util.Objects;

/**
 * 글 요약. 직접 쓴 요약인지 자동으로 만든 요약인지 구분해 저장한다 (스펙 6. 글 속성의 요약).
 * 자동 요약을 만드는 방식은 동기화가 정한다.
 */
public record Summary(String text, Source source) {

    public enum Source {
        MANUAL,
        AUTO
    }

    public Summary {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(source, "source");
        if (source == Source.MANUAL && text.isBlank()) {
            throw new IllegalArgumentException("직접 쓴 요약은 비어 있을 수 없다. 비어 있으면 자동 요약을 쓴다");
        }
    }

    public static Summary manual(String text) {
        return new Summary(text, Source.MANUAL);
    }

    public static Summary auto(String text) {
        return new Summary(text, Source.AUTO);
    }
}
