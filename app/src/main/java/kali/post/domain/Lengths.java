package kali.post.domain;

import java.nio.charset.StandardCharsets;

/** 글 칸 길이 제한 (스펙 6. 글 속성의 길이 제한). 넘으면 자르지 않고 거부한다. */
final class Lengths {

    static final int SLUG_CHARS = 200;
    static final int TITLE_CHARS = 255;
    static final int SOURCE_PATH_CHARS = 1024;
    static final int BODY_BYTES = 16_777_215;
    static final int MANUAL_SUMMARY_BYTES = 65_535;
    static final int TAG_CHARS = 100;

    private Lengths() {}

    // MySQL VARCHAR(n)의 n은 문자 수다. 이모지처럼 UTF-16 두 칸인 문자도 한 자로 센다
    static void requireChars(String name, String value, int max) {
        int chars = value.codePointCount(0, value.length());
        if (chars > max) {
            throw new IllegalArgumentException(name + "은(는) " + max + "자까지다: " + chars + "자");
        }
    }

    // MySQL TEXT·MEDIUMTEXT의 최대는 바이트 수다
    static void requireUtf8Bytes(String name, String value, int max) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > max) {
            throw new IllegalArgumentException(name + "은(는) UTF-8 " + max + "바이트까지다: " + bytes + "바이트");
        }
    }
}
