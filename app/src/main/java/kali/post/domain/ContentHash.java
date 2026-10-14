package kali.post.domain;

import java.util.Objects;

/**
 * 내용 해시와 그 해시를 계산한 방식의 버전 (스펙 5. 발행의 다시 발행할 때).
 * 해시에 넣는 값과 계산은 동기화가 한다. 태그 형태(미정 D, R)와 이미지 경로(미정 Q)가 정해져야 계산할 수 있다.
 */
public record ContentHash(String value, int version) {

    public ContentHash {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("내용 해시는 비어 있을 수 없다");
        }
        if (version < 1) {
            throw new IllegalArgumentException("해시 방식 버전은 1 이상이다: " + version);
        }
    }

    boolean sameVersion(ContentHash other) {
        return version == other.version;
    }
}
