package kali.sync.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kali.post.domain.PostContent;
import kali.post.domain.PostId;
import kali.post.domain.PostTitle;
import kali.post.domain.Slug;
import kali.post.domain.Summary;
import kali.post.domain.Tag;

/**
 * 발행 대상 노트 하나를 글 내용으로 읽는다 (스펙 3. 글 식별, 5. 발행의 문제 있는 노트, 6. 글 속성). 다른 노트와 견줘야 하는 검사(같은
 * {@code id}, 같은 동기화의 slug 겹침, 다른 글의 slug)는 동기화가 한다.
 */
public final class NoteParser {

    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})( (\\d{2}:\\d{2}:\\d{2}))?");
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private final ImageResolver images;
    private final Function<String, String> imageUrl;

    /**
     * @param imageUrl 이미지 저장소 키로 본문에 넣을 주소를 만든다. 올리기 전에 길이 제한을 검사하려고 주소를 먼저 만든다
     */
    public NoteParser(ImageResolver images, Function<String, String> imageUrl) {
        this.images = images;
        this.imageUrl = imageUrl;
    }

    /** @throws NoteSkippedException 노트를 건너뛰어야 할 때. 이유는 메시지에 있다 */
    public PostDraft parse(Note note) {
        Map<String, Object> fm = note.frontmatter();
        UUID id = id(fm.get("id"));
        try {
            Slug slug = slug(fm.get("slug"));
            String title = PostTitle.resolve(string(fm.get("title"), "title"), note.path());
            LocalDate date = date(fm.get("date"));
            List<Tag> tags = tags(fm.get("tags"));
            String body = note.body().replace("\r\n", "\n").replace('\r', '\n');
            Map<String, String> found = new LinkedHashMap<>();
            for (ImageEmbeds.Embed embed : ImageEmbeds.find(body)) {
                found.putIfAbsent(embed.target(), images.resolve(embed.target()));
            }
            String storedBody = ImageEmbeds.rewrite(body, target -> imageUrl.apply(found.get(target)));
            String manualSummary = string(fm.get("summary"), "summary");
            Summary summary = manualSummary == null || manualSummary.isBlank()
                    ? Summary.auto(AutoSummary.from(body))
                    : Summary.manual(manualSummary);
            PostContent content = new PostContent(title, slug, storedBody, summary, date, note.path(), tags);
            Map<String, String> keys = new LinkedHashMap<>();
            found.values().forEach(path -> keys.put(path, path));
            return new PostDraft(id, content, body, keys);
        } catch (IllegalArgumentException e) {
            throw new NoteSkippedException(e.getMessage(), id);
        }
    }

    private static UUID id(Object value) {
        if (value == null) {
            throw new NoteSkippedException("id가 없음", null);
        }
        try {
            return PostId.parse(value instanceof String s ? s : null);
        } catch (IllegalArgumentException e) {
            throw new NoteSkippedException("id가 UUID 형식이 아님: " + value, null);
        }
    }

    private static Slug slug(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("slug가 없음");
        }
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("slug 형식이 틀림: " + value);
        }
        return new Slug(s);
    }

    private static String string(Object value, String name) {
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException(name + "이(가) 문자열이 아님: " + value);
    }

    // YAML이 날짜로 읽기 전의 글자로 검사한다 (볼트 읽기가 날짜를 문자열로 둔다)
    private static LocalDate date(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        Matcher m = value instanceof String s ? DATE.matcher(s) : null;
        if (m == null || !m.matches()) {
            throw new IllegalArgumentException("date 형식이 틀림 (YYYY-MM-DD 또는 YYYY-MM-DD HH:mm:ss): " + value);
        }
        try {
            if (m.group(3) != null) {
                LocalTime.parse(m.group(3), TIME);
            }
            return LocalDate.parse(m.group(1), DAY);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("없는 날짜: " + value);
        }
    }

    private static List<Tag> tags(Object value) {
        List<Tag> tags = new ArrayList<>();
        if (value == null) {
            return tags;
        }
        List<?> items = value instanceof List<?> list ? list : value instanceof String s ? List.of(s) : null;
        if (items == null) {
            throw new IllegalArgumentException("tags가 목록이나 문자열이 아님: " + value);
        }
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            if (!(item instanceof String s)) {
                throw new IllegalArgumentException("tags 항목이 문자열이 아님: " + item);
            }
            Tag.parse(s).ifPresent(tags::add);
        }
        return tags;
    }
}
