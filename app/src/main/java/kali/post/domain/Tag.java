package kali.post.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 글에 다는 이름 (스펙 6. 글 속성의 태그). 소문자이고, 공백이 없고, 100자까지다. 중첩 태그({@code 책/지대넓})는 {@code /}까지 포함한
 * 이름 하나다.
 */
public record Tag(String name) implements Comparable<Tag> {

    public Tag {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("태그는 비어 있을 수 없다");
        }
        if (name.codePoints().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("태그에 공백이 있다: " + name);
        }
        if (name.startsWith("#")) {
            throw new IllegalArgumentException("태그 앞의 #는 떼고 만든다: " + name);
        }
        if (!name.equals(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("태그는 소문자로 만든다: " + name);
        }
        Lengths.requireChars("태그", name, Lengths.TAG_CHARS);
    }

    /**
     * frontmatter {@code tags}의 항목 하나를 태그로 읽는다. 앞뒤 공백과 앞에 붙은 {@code #}를 떼고 소문자로 바꾼다.
     *
     * @return 뗀 뒤 비어 있으면 빈 값 (그 항목은 무시한다)
     * @throws IllegalArgumentException 공백이 들었거나 100자를 넘을 때
     */
    public static Optional<Tag> parse(String raw) {
        String name = raw.strip();
        while (name.startsWith("#")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Tag(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public int compareTo(Tag other) {
        return name.compareTo(other.name);
    }
}
