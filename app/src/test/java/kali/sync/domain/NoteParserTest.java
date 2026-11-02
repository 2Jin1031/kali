package kali.sync.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kali.post.domain.PostContent;
import kali.post.domain.Summary;
import kali.post.domain.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NoteParserTest {

    static final String ID = "c97535fc-f85c-47c7-8d47-67675b1a3bcf";

    final NoteParser parser = new NoteParser(
            new ImageResolver(List.of("image/meetup-01.png", "image/meetup-02.jpg", "a/same.png", "b/same.png")),
            key -> "https://img/" + key);

    static Map<String, Object> fm() {
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("id", ID);
        fm.put("slug", "winset");
        fm.put("publish", true);
        return fm;
    }

    static Map<String, Object> with(String key, Object value) {
        Map<String, Object> fm = fm();
        fm.put(key, value);
        return fm;
    }

    PostDraft parse(Map<String, Object> fm, String body) {
        return parser.parse(Note.of("글/윈셋 이야기.md", fm, body));
    }

    String skipReason(Map<String, Object> fm, String body) {
        try {
            parse(fm, body);
        } catch (NoteSkippedException e) {
            return e.getMessage();
        }
        throw new AssertionError("건너뛰지 않음");
    }

    @Test
    void frontmatter를_글_내용으로_읽는다() {
        Map<String, Object> fm = fm();
        fm.put("title", "[초록] 윈셋");
        fm.put("date", "2026-08-27");
        fm.put("tags", List.of("초록", "밋업"));
        fm.put("summary", "직접 쓴 요약");

        PostDraft draft = parse(fm, "본문");

        PostContent content = draft.content();
        assertThat(draft.id()).isEqualTo(UUID.fromString(ID));
        assertThat(content.title()).isEqualTo("[초록] 윈셋");
        assertThat(content.slug().value()).isEqualTo("winset");
        assertThat(content.date()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(content.tags()).containsExactly(new Tag("밋업"), new Tag("초록"));
        assertThat(content.summary()).isEqualTo(Summary.manual("직접 쓴 요약"));
        assertThat(content.sourcePath()).isEqualTo("글/윈셋 이야기.md");
    }

    @Test
    void title이_없으면_파일_이름을_제목으로_쓴다() {
        assertThat(parse(with("title", "  "), "본문").content().title()).isEqualTo("윈셋 이야기");
    }

    @Test
    void 대문자_id는_소문자로_읽는다() {
        assertThat(parse(with("id", ID.toUpperCase()), "본문").id()).isEqualTo(UUID.fromString(ID));
    }

    @Test
    void id가_없으면_건너뛰고_어느_글인지_모른다() {
        Map<String, Object> fm = with("id", null);

        assertThatThrownBy(() -> parse(fm, "본문"))
                .isInstanceOfSatisfying(
                        NoteSkippedException.class, e -> assertThat(e.id()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-2-3-4-5"})
    void id가_UUID_형식이_아니면_건너뛴다(String id) {
        assertThat(skipReason(with("id", id), "본문")).contains("UUID");
    }

    @Test
    void id를_읽은_뒤에_건너뛰면_그_id를_알려준다() {
        assertThatThrownBy(() -> parse(with("slug", null), "본문"))
                .isInstanceOfSatisfying(
                        NoteSkippedException.class, e -> assertThat(e.id()).contains(UUID.fromString(ID)));
    }

    @Test
    void slug가_없거나_형식이_틀리면_건너뛴다() {
        assertThat(skipReason(with("slug", null), "본문")).contains("slug");
        assertThat(skipReason(with("slug", "Win Set"), "본문")).contains("slug");
        assertThat(skipReason(with("slug", 2026), "본문")).contains("slug");
    }

    @Test
    void title이_문자열이_아니면_건너뛴다() {
        assertThat(skipReason(with("title", 2026), "본문")).contains("title");
        assertThat(skipReason(with("title", List.of("a")), "본문")).contains("title");
    }

    @Test
    void date는_날짜_부분만_쓰고_비어_있으면_없는_것으로_본다() {
        assertThat(parse(with("date", "2026-08-27 23:59:59"), "본문").content().date())
                .isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(parse(with("date", ""), "본문").content().date()).isNull();
        assertThat(parse(with("date", null), "본문").content().date()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"2026-10-14T10:00:00", "{{date}}", "2026-02-30", "2026-10-14 25:00:00", "2026/10/14", "26-10-14"
            })
    void date_형식이_틀리거나_없는_날짜면_건너뛴다(String date) {
        assertThat(skipReason(with("date", date), "본문")).contains(date);
    }

    @Test
    void date가_문자열이_아니면_건너뛴다() {
        assertThat(skipReason(with("date", 20261014), "본문")).contains("date");
    }

    @Test
    void tags는_문자열_하나도_받고_비어_있으면_태그가_없다() {
        assertThat(parse(with("tags", "#DB"), "본문").content().tags()).containsExactly(new Tag("db"));
        assertThat(parse(with("tags", null), "본문").content().tags()).isEmpty();
        assertThat(parse(with("tags", java.util.Arrays.asList("db", null, "#")), "본문")
                        .content()
                        .tags())
                .containsExactly(new Tag("db"));
    }

    @Test
    void tags가_목록이나_문자열이_아니거나_공백이_들면_건너뛴다() {
        assertThat(skipReason(with("tags", Map.of("a", "b")), "본문")).contains("tags");
        assertThat(skipReason(with("tags", 2026), "본문")).contains("tags");
        assertThat(skipReason(with("tags", List.of(2026)), "본문")).contains("tags");
        assertThat(skipReason(with("tags", "책 공부"), "본문")).contains("공백");
    }

    @Test
    void summary가_비어_있으면_본문으로_자동_요약을_만든다() {
        assertThat(parse(with("summary", " "), "## 제목\n첫 문장").content().summary())
                .isEqualTo(Summary.auto("제목 첫 문장"));
    }

    @Test
    void summary가_문자열이_아니면_건너뛴다() {
        assertThat(skipReason(with("summary", List.of("a")), "본문")).contains("summary");
    }

    @Test
    void 본문_이미지의_경로를_바꾸고_해시용_본문은_그대로_둔다() {
        PostDraft draft = parse(fm(), "![[meetup-01.png]] ![[meetup-02.jpg|300]]\r\n끝");

        assertThat(draft.content().body())
                .isEqualTo("![meetup-01.png](https://img/image/meetup-01.png) "
                        + "![meetup-02.jpg](https://img/image/meetup-02.jpg)\n끝");
        assertThat(draft.originalBody()).isEqualTo("![[meetup-01.png]] ![[meetup-02.jpg|300]]\n끝");
        assertThat(draft.images()).containsOnlyKeys("image/meetup-01.png", "image/meetup-02.jpg");
    }

    @Test
    void 이미지를_찾을_수_없거나_이름이_파일_둘_이상에_맞으면_건너뛴다() {
        assertThat(skipReason(fm(), "![[없음.png]]")).contains("없음.png");
        assertThat(skipReason(fm(), "![[same.png]]")).contains("same.png");
    }

    @Test
    void 경로가_있는_이미지는_그_경로로_찾는다() {
        assertThat(parse(fm(), "![[a/same.png]]").images()).containsOnlyKeys("a/same.png");
    }

    @Test
    void 길이_제한을_넘으면_건너뛴다() {
        assertThat(skipReason(with("slug", "a".repeat(201)), "본문")).contains("200");
        assertThat(skipReason(with("title", "가".repeat(256)), "본문")).contains("255");
        assertThat(skipReason(with("tags", List.of("가".repeat(101))), "본문")).contains("100");
    }
}
