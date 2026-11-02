package kali.sync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kali.post.domain.ContentHash;
import kali.post.domain.Post;
import kali.post.domain.PostContent;
import kali.post.domain.PostStatus;
import kali.post.domain.Slug;
import kali.post.domain.Summary;
import kali.post.domain.Tag;
import kali.sync.domain.Note;
import kali.sync.domain.SkippedNote;
import kali.sync.domain.SyncReport;
import kali.sync.infra.InMemoryImageStore;
import org.junit.jupiter.api.Test;

class PostSyncServiceTest {

    static final String ID = "c97535fc-f85c-47c7-8d47-67675b1a3bcf";
    static final String OTHER_ID = "0b6f2c1e-6a4d-4a8e-9c1f-2d3e4f5a6b7c";
    static final Instant FIRST = Instant.parse("2026-10-14T03:00:00Z");
    static final Instant SECOND = Instant.parse("2026-10-20T12:00:00Z");

    final InMemoryPostRepository posts = new InMemoryPostRepository();
    final InMemoryImageStore images = new InMemoryImageStore();

    SyncReport sync(InMemoryVault vault, Instant now) {
        return new PostSyncService(posts, images, Clock.fixed(now, ZoneOffset.UTC)).sync(vault);
    }

    static Map<String, Object> fm(String id, String slug) {
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("id", id);
        fm.put("slug", slug);
        fm.put("publish", true);
        return fm;
    }

    static Map<String, Object> with(Map<String, Object> fm, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(fm);
        copy.put(key, value);
        return copy;
    }

    Post post(String id) {
        return posts.findById(UUID.fromString(id)).orElseThrow();
    }

    @Test
    void publish_true인_노트를_처음_발행한다() {
        InMemoryVault vault = new InMemoryVault()
                .note("글/윈셋.md", with(fm(ID, "winset"), "tags", List.of("DB", "#spring", "db")), "윈셋 본문")
                .note("메모.md", Map.of("title", "발행 안 함"), "본문");

        SyncReport report = sync(vault, FIRST);

        Post post = post(ID);
        assertThat(report.published()).containsExactly("글/윈셋.md");
        assertThat(post.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.title()).isEqualTo("윈셋");
        assertThat(post.tags()).containsExactly(new Tag("db"), new Tag("spring"));
        assertThat(post.summary()).isEqualTo(Summary.auto("윈셋 본문"));
        assertThat(post.firstPublishedAt()).isEqualTo(FIRST);
        assertThat(post.contentUpdatedAt()).isEqualTo(FIRST);
        assertThat(post.displayDate()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(posts.findAll()).hasSize(1);
    }

    @Test
    void 대문자_id_노트는_소문자_id_글과_같은_글이다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), FIRST);

        SyncReport report = sync(new InMemoryVault().note("a.md", fm(ID.toUpperCase(), "winset"), "본문"), SECOND);

        assertThat(report.unchanged()).containsExactly("a.md");
        assertThat(posts.findAll()).hasSize(1);
    }

    @Test
    void 본문_이미지를_저장소에_올리고_본문의_경로를_바꾼다() {
        InMemoryVault vault = new InMemoryVault()
                .note("글/a.md", fm(ID, "winset"), "앞\n![[meetup-01.png]]\n뒤")
                .image("image/meetup-01.png", "png 바이트");

        sync(vault, FIRST);

        assertThat(post(ID).body()).isEqualTo("앞\n![meetup-01.png](https://images.test/image/meetup-01.png)\n뒤");
        assertThat(images.stored).containsOnlyKeys("image/meetup-01.png");
    }

    @Test
    void 내용이_같으면_다시_쓰지_않고_이미지도_다시_올리지_않는다() {
        InMemoryVault vault =
                new InMemoryVault().note("a.md", fm(ID, "winset"), "![[x.png]]").image("x.png", "1");
        sync(vault, FIRST);
        int saves = posts.saves;

        SyncReport report = sync(vault, SECOND);

        assertThat(report.unchanged()).containsExactly("a.md");
        assertThat(posts.saves).isEqualTo(saves);
        assertThat(images.puts).isEqualTo(1);
        assertThat(post(ID).contentUpdatedAt()).isEqualTo(FIRST);
    }

    @Test
    void 이름이_같은_이미지의_파일만_바뀌면_수정으로_보지_않는다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "![[x.png]]").image("x.png", "1"), FIRST);

        SyncReport report = sync(
                new InMemoryVault().note("a.md", fm(ID, "winset"), "![[x.png]]").image("x.png", "2"), SECOND);

        assertThat(report.unchanged()).containsExactly("a.md");
        assertThat(images.stored.get("x.png")).asString().isEqualTo("1");
    }

    @Test
    void 내용이_바뀌면_다시_쓰고_수정_시각을_바꾼다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), FIRST);

        SyncReport report = sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "고친 본문"), SECOND);

        assertThat(report.revised()).containsExactly("a.md");
        assertThat(post(ID).body()).isEqualTo("고친 본문");
        assertThat(post(ID).contentUpdatedAt()).isEqualTo(SECOND);
        assertThat(post(ID).firstPublishedAt()).isEqualTo(FIRST);
    }

    @Test
    void 태그_순서나_대소문자만_바뀌면_수정이_아니다() {
        sync(new InMemoryVault().note("a.md", with(fm(ID, "winset"), "tags", List.of("db", "spring")), "본문"), FIRST);

        SyncReport report = sync(
                new InMemoryVault().note("a.md", with(fm(ID, "winset"), "tags", List.of("Spring", "DB")), "본문"),
                SECOND);

        assertThat(report.unchanged()).containsExactly("a.md");
    }

    @Test
    void 줄바꿈만_CRLF로_바뀌면_수정이_아니다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "첫 줄\n둘째 줄"), FIRST);

        SyncReport report = sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "첫 줄\r\n둘째 줄"), SECOND);

        assertThat(report.unchanged()).containsExactly("a.md");
    }

    @Test
    void 노트를_옮기기만_하면_경로만_바꾸고_수정_시각은_그대로다() {
        sync(new InMemoryVault().note("a.md", with(fm(ID, "winset"), "title", "윈셋"), "본문"), FIRST);

        SyncReport report =
                sync(new InMemoryVault().note("개념/a.md", with(fm(ID, "winset"), "title", "윈셋"), "본문"), SECOND);

        assertThat(report.moved()).containsExactly("개념/a.md");
        assertThat(post(ID).sourcePath()).isEqualTo("개념/a.md");
        assertThat(post(ID).contentUpdatedAt()).isEqualTo(FIRST);
    }

    @Test
    void 발행_대상에서_빠진_글은_발행_취소한다() {
        sync(
                new InMemoryVault().note("a.md", fm(ID, "winset"), " 본문").note("b.md", fm(OTHER_ID, "other"), "본문"),
                FIRST);

        SyncReport report = sync(
                new InMemoryVault()
                        .note("a.md", fm(ID, "winset"), " 본문")
                        .note("b.md", with(fm(OTHER_ID, "other"), "publish", false), "본문"),
                SECOND);

        assertThat(report.unpublished()).containsExactly("other");
        assertThat(post(OTHER_ID).status()).isEqualTo(PostStatus.UNPUBLISHED);
        assertThat(post(OTHER_ID).firstPublishedAt()).isEqualTo(FIRST);
    }

    @Test
    void 발행_취소한_글이_다시_발행되면_처음_발행_시각은_그대로다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문").note("b.md", fm(OTHER_ID, "other"), "본문"), FIRST);
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), SECOND);

        SyncReport report = sync(
                new InMemoryVault().note("a.md", fm(ID, "winset"), "본문").note("b.md", fm(OTHER_ID, "other"), "본문"),
                SECOND.plusSeconds(60));

        assertThat(report.republished()).containsExactly("b.md");
        assertThat(post(OTHER_ID).status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post(OTHER_ID).firstPublishedAt()).isEqualTo(FIRST);
    }

    @Test
    void 발행_대상이_0개면_아무것도_바꾸지_않고_실패한다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), FIRST);

        assertThatThrownBy(() -> sync(new InMemoryVault().note("a.md", Map.of(), "본문"), SECOND))
                .isInstanceOf(NoPublishTargetException.class);
        assertThat(post(ID).status()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void 문제_있는_노트는_건너뛰고_이유를_보고하고_나머지는_반영한다() {
        InMemoryVault vault = new InMemoryVault()
                .note("좋음.md", fm(ID, "winset"), "본문")
                .note("id없음.md", with(fm(ID, "a"), "id", null), "본문")
                .note("날짜틀림.md", with(fm(OTHER_ID, "b"), "date", "{{date}}"), "본문");

        SyncReport report = sync(vault, FIRST);

        assertThat(report.published()).containsExactly("좋음.md");
        assertThat(report.skipped()).extracting(SkippedNote::path).containsExactlyInAnyOrder("id없음.md", "날짜틀림.md");
        assertThat(report.skipped())
                .allSatisfy(skipped -> assertThat(skipped.reason()).isNotBlank());
    }

    @Test
    void 건너뛴_노트와_같은_id의_글은_발행_취소하지_않는다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문").note("b.md", fm(OTHER_ID, "other"), "본문"), FIRST);

        SyncReport report = sync(
                new InMemoryVault()
                        .note("a.md", fm(ID, "winset"), "본문")
                        .note("b.md", with(fm(OTHER_ID, "other"), "title", 2026), "본문"),
                SECOND);

        assertThat(report.skipped()).extracting(SkippedNote::path).containsExactly("b.md");
        assertThat(report.unpublished()).isEmpty();
        assertThat(post(OTHER_ID).status()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void id_형식이_틀려_건너뛴_노트는_어느_글인지_몰라_보호하지_않는다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문").note("b.md", fm(OTHER_ID, "other"), "본문"), FIRST);

        sync(
                new InMemoryVault().note("a.md", fm(ID, "winset"), "본문").note("b.md", fm("not-a-uuid", "other"), "본문"),
                SECOND);

        assertThat(post(OTHER_ID).status()).isEqualTo(PostStatus.UNPUBLISHED);
    }

    @Test
    void 이미지를_찾을_수_없는_노트는_건너뛴다() {
        SyncReport report = sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "![[없음.png]]"), FIRST);

        assertThat(report.skipped())
                .singleElement()
                .satisfies(skipped -> assertThat(skipped.reason()).contains("없음.png"));
        assertThat(posts.findAll()).isEmpty();
    }

    @Test
    void 같은_id를_가진_노트가_둘_이상이면_모두_건너뛴다() {
        SyncReport report = sync(
                new InMemoryVault().note("a.md", fm(ID, "winset"), "본문").note("b.md", fm(ID, "other"), "본문"), FIRST);

        assertThat(report.skipped()).extracting(SkippedNote::path).containsExactlyInAnyOrder("a.md", "b.md");
        assertThat(posts.findAll()).isEmpty();
    }

    @Test
    void 같은_동기화에서_새_노트끼리_slug가_겹치면_모두_건너뛴다() {
        SyncReport report = sync(
                new InMemoryVault().note("a.md", fm(ID, "winset"), "본문").note("b.md", fm(OTHER_ID, "winset"), "본문"),
                FIRST);

        assertThat(report.skipped()).extracting(SkippedNote::path).containsExactlyInAnyOrder("a.md", "b.md");
        assertThat(posts.findAll()).isEmpty();
    }

    @Test
    void 같은_동기화에서_slug가_겹치면_그_slug를_가진_글의_노트만_반영한다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), FIRST);

        SyncReport report = sync(
                new InMemoryVault().note("a.md", fm(ID, "winset"), "고친 본문").note("b.md", fm(OTHER_ID, "winset"), "본문"),
                SECOND);

        assertThat(report.revised()).containsExactly("a.md");
        assertThat(report.skipped()).extracting(SkippedNote::path).containsExactly("b.md");
    }

    @Test
    void 겹침을_셀_때_다른_이유로_건너뛴_노트는_세지_않는다() {
        SyncReport report = sync(
                new InMemoryVault()
                        .note("a.md", fm(ID, "winset"), "본문")
                        .note("b.md", with(fm(OTHER_ID, "winset"), "date", "2026-02-30"), "본문"),
                FIRST);

        assertThat(report.published()).containsExactly("a.md");
        assertThat(report.skipped()).extracting(SkippedNote::path).containsExactly("b.md");
    }

    @Test
    void 다른_글이_예전에_쓴_slug는_쓸_수_없다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), FIRST);
        sync(new InMemoryVault().note("a.md", fm(ID, "winset-2"), "본문"), SECOND);

        SyncReport report = sync(
                new InMemoryVault().note("a.md", fm(ID, "winset-2"), "본문").note("b.md", fm(OTHER_ID, "winset"), "본문"),
                SECOND);

        assertThat(report.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.path()).isEqualTo("b.md");
            assertThat(skipped.reason()).contains("winset");
        });
        assertThat(posts.findById(UUID.fromString(OTHER_ID))).isEmpty();
    }

    @Test
    void 자기_옛_slug로_돌아가면_받아들인다() {
        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), FIRST);
        sync(new InMemoryVault().note("a.md", fm(ID, "winset-2"), "본문"), SECOND);

        sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "본문"), SECOND);

        assertThat(post(ID).slug()).isEqualTo(new Slug("winset"));
        assertThat(post(ID).previousSlugs()).containsExactly(new Slug("winset-2"));
    }

    @Test
    void 해시_방식이_바뀐_글은_내용을_다시_쓰고_수정_시각은_그대로다() {
        PostContent old = new PostContent("윈셋", new Slug("winset"), "옛 방식 본문", Summary.auto(""), null, "a.md");
        posts.save(Post.publish(UUID.fromString(ID), old, new ContentHash("old", 99), FIRST));

        SyncReport report = sync(new InMemoryVault().note("a.md", fm(ID, "winset"), "고친 본문"), SECOND);

        assertThat(report.rehashed()).containsExactly("a.md");
        assertThat(post(ID).body()).isEqualTo("고친 본문");
        assertThat(post(ID).contentUpdatedAt()).isEqualTo(FIRST);
    }

    @Test
    void frontmatter를_읽지_못한_노트에_publish_true_줄이_있으면_보고한다() {
        InMemoryVault vault = new InMemoryVault().note("a.md", fm(ID, "winset"), "본문");
        vault.notes.put("깨짐.md", Note.unreadable("깨짐.md", "---\npublish: true\ntitle: [\n---\n", "YAML 오류"));
        vault.notes.put("깨짐2.md", Note.unreadable("깨짐2.md", "---\ntitle: [\n---\n", "YAML 오류"));

        SyncReport report = sync(vault, FIRST);

        assertThat(report.skipped()).extracting(SkippedNote::path).containsExactly("깨짐.md");
    }
}
