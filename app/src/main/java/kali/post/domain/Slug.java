package kali.post.domain;

import java.util.regex.Pattern;

/** URL에 쓰는 글 이름. 형식은 영문 소문자, 숫자, 하이픈 (스펙 3. 글 식별). */
public record Slug(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9-]+");

    public Slug {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("slug 형식은 영문 소문자, 숫자, 하이픈이다: " + value);
        }
    }
}
