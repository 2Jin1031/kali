package kali.sync.domain;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 본문의 첨부 이미지 찾기와 경로 바꾸기 (스펙 5. 발행의 다시 발행할 때). 옵시디언 임베드 {@code ![[이름.png]]}와 마크다운 이미지
 * {@code ![설명](경로.png)} 중 볼트 안 파일을 가리키는 것만 다룬다. 코드 블록과 인라인 코드 안은 건드리지 않는다. 이미지가 아닌 노트
 * 임베드 {@code ![[노트]]}는 스펙 미정 V라 그대로 둔다.
 */
public final class ImageEmbeds {

    static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif");

    // ![[이름]], ![[이름|크기]], ![[이름#조각]]
    private static final Pattern WIKI = Pattern.compile("!\\[\\[([^\\]|#\\n]+)(?:[|#][^\\]\\n]*)?]]");
    // ![설명](경로), ![설명](<경로>), ![설명](경로 "제목")
    private static final Pattern MARKDOWN =
            Pattern.compile("!\\[([^\\]\\n]*)]\\(\\s*(?:<([^>\\n]+)>|([^)\\s]+))(?:\\s+\"[^\"\\n]*\")?\\s*\\)");
    private static final Pattern URL_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
    private static final Pattern INLINE_CODE = Pattern.compile("(`+)[^`]*?\\1");

    private ImageEmbeds() {}

    /**
     * @param target 볼트에서 찾을 이름이나 경로 (마크다운 이미지는 %-인코딩을 푼 값)
     * @param alt 바꾼 뒤 보일 설명
     */
    public record Embed(int start, int end, String target, String alt) {}

    public static List<Embed> find(String body) {
        List<int[]> code = codeRanges(body);
        List<Embed> embeds = new ArrayList<>();
        Matcher wiki = WIKI.matcher(body);
        while (wiki.find()) {
            String target = wiki.group(1).strip();
            if (isImage(target) && !inside(code, wiki.start())) {
                embeds.add(new Embed(wiki.start(), wiki.end(), target, fileName(target)));
            }
        }
        Matcher markdown = MARKDOWN.matcher(body);
        while (markdown.find()) {
            String raw = markdown.group(2) != null ? markdown.group(2) : markdown.group(3);
            if (URL_SCHEME.matcher(raw).find() || raw.startsWith("/") || inside(code, markdown.start())) {
                continue;
            }
            String target = decode(raw);
            if (isImage(target)) {
                embeds.add(new Embed(markdown.start(), markdown.end(), target, markdown.group(1)));
            }
        }
        embeds.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return embeds;
    }

    /** 찾은 이미지를 {@code ![설명](주소)}로 바꾼다. */
    public static String rewrite(String body, Function<String, String> urlOfTarget) {
        StringBuilder out = new StringBuilder(body.length());
        int last = 0;
        for (Embed embed : find(body)) {
            out.append(body, last, embed.start())
                    .append("![")
                    .append(embed.alt())
                    .append("](")
                    .append(urlOfTarget.apply(embed.target()))
                    .append(')');
            last = embed.end();
        }
        return out.append(body, last, body.length()).toString();
    }

    private static boolean isImage(String target) {
        int dot = target.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(target.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    // 여는 펜스와 같은 문자로 같은 개수 이상만 있는 줄이 닫는 펜스다
    private static boolean closes(String line, String fence) {
        String stripped = line.strip();
        char mark = fence.charAt(0);
        return stripped.length() >= fence.length() && stripped.chars().allMatch(c -> c == mark);
    }

    private static String fileName(String target) {
        return target.substring(target.lastIndexOf('/') + 1);
    }

    private static String decode(String raw) {
        try {
            // URLDecoder는 +를 공백으로 바꾸므로 +는 그대로 둔다
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    private static boolean inside(List<int[]> ranges, int index) {
        for (int[] range : ranges) {
            if (range[0] <= index && index < range[1]) {
                return true;
            }
        }
        return false;
    }

    // 펜스 코드 블록(``` 또는 ~~~)과 인라인 코드의 [시작, 끝) 위치
    private static List<int[]> codeRanges(String body) {
        List<int[]> ranges = new ArrayList<>();
        String fence = null;
        int fenceStart = 0;
        int lineStart = 0;
        while (lineStart <= body.length()) {
            int newline = body.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? body.length() : newline;
            String line = body.substring(lineStart, lineEnd);
            Matcher opening = FENCE.matcher(line);
            if (fence == null && opening.find()) {
                fence = opening.group(1);
                fenceStart = lineStart;
            } else if (fence != null && closes(line, fence)) {
                ranges.add(new int[] {fenceStart, lineEnd});
                fence = null;
            } else if (fence == null) {
                Matcher inline = INLINE_CODE.matcher(line);
                while (inline.find()) {
                    ranges.add(new int[] {lineStart + inline.start(), lineStart + inline.end()});
                }
            }
            if (newline < 0) {
                break;
            }
            lineStart = newline + 1;
        }
        if (fence != null) {
            ranges.add(new int[] {fenceStart, body.length()}); // 닫히지 않은 코드 블록은 끝까지
        }
        return ranges;
    }
}
