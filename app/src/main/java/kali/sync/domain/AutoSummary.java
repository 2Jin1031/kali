package kali.sync.domain;

import java.util.regex.Pattern;

/**
 * {@code summary}가 비어 있을 때 본문 앞부분으로 만드는 요약 (스펙 6. 글 속성의 요약). 마크다운 기호와 코드 블록, 이미지를 빼고 앞
 * {@value #MAX_CHARS}자를 쓴다. 방식이 바뀌었을 때 기존 자동 요약을 다시 만들지는 스펙 미정 K다.
 */
public final class AutoSummary {

    static final int MAX_CHARS = 150;

    private static final Pattern FENCED_CODE = Pattern.compile("(?ms)^ {0,3}(```|~~~).*?^ {0,3}\\1[`~]*\\s*$");
    private static final Pattern EMBED = Pattern.compile("!\\[\\[[^\\]]*]]|!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[(?:[^\\]|]*\\\\?\\|)?([^\\]]*)]]");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>\\n]+>");
    private static final Pattern LINE_MARKS =
            Pattern.compile("(?m)^\\s*(?:#{1,6}\\s+|>\\s*|[-*+]\\s+|\\d+\\.\\s+|[-*_]{3,}\\s*$)");
    private static final Pattern INLINE_MARKS = Pattern.compile("\\*\\*|__|~~|`|==");

    private AutoSummary() {}

    public static String from(String body) {
        String text = FENCED_CODE.matcher(body).replaceAll(" ");
        text = EMBED.matcher(text).replaceAll(" ");
        text = WIKILINK.matcher(text).replaceAll("$1");
        text = LINK.matcher(text).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = LINE_MARKS.matcher(text).replaceAll(" ");
        text = INLINE_MARKS.matcher(text).replaceAll("");
        text = text.replaceAll("\\s+", " ").strip();
        if (text.codePointCount(0, text.length()) <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_CHARS)).strip() + "…";
    }
}
