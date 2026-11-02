package kali.post.domain;

import java.util.regex.Pattern;

/** URL에 쓰는 글 이름. 형식은 영문 소문자, 숫자, 하이픈이고 200자까지다 (스펙 3. 글 식별, 6. 글 속성의 길이 제한). */
public record Slug(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9-]+");

    public Slug {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("slug 형식은 영문 소문자, 숫자, 하이픈이다: " + value);
        }
        Lengths.requireChars("slug", value, Lengths.SLUG_CHARS);
    }
}
