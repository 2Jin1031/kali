package kali.post.domain;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** frontmatter {@code id}를 글 id로 읽기 (스펙 3. 글 식별). */
public final class PostId {

    // UUID.fromString은 "1-2-3-4-5"처럼 자릿수가 모자란 값도 받으므로 자릿수를 따로 검사한다
    private static final Pattern FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private PostId() {}

    /**
     * 대소문자를 구분하지 않고 UUID로 읽는다. 대문자가 섞여 있으면 소문자로 바꾼 것과 같은 글이다.
     *
     * @throws IllegalArgumentException 16진수 8-4-4-4-12자리가 아닐 때
     */
    public static UUID parse(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("id는 UUID 형식(16진수 8-4-4-4-12자리)이어야 한다: " + value);
        }
        return UUID.fromString(value.toLowerCase(Locale.ROOT));
    }
}
