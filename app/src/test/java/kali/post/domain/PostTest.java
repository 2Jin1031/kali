package kali.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostTest {

    static final UUID ID = UUID.fromString("0b6f2c1e-6a4d-4a8e-9c1f-2d3e4f5a6b7c");
    static final Instant PUBLISHED_AT = Instant.parse("2026-10-14T03:00:00Z");
    static final Instant LATER = Instant.parse("2026-10-20T12:00:00Z");
    static final ContentHash HASH = new ContentHash("hash-1", 1);

    static PostContent content(String slug) {
        return new PostContent("윈셋", new Slug(slug), "본문", Summary.manual("요약"), null, "notes/윈셋.md");
    }

    static Post published() {
        return Post.publish(ID, content("winset"), HASH, PUBLISHED_AT);
    }

    @Test
    void 처음_발행하면_PUBLISHED이고_처음_발행_시각과_수정_시각이_발행_시각이다() {
        Post post = published();

        assertThat(post.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.firstPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.contentUpdatedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.previousSlugs()).isEmpty();
    }

    @Test
    void 발행을_취소해도_처음_발행_시각과_옛_slug가_남는다() {
        Post post = published();
        post.revise(content("winset-2"), new ContentHash("hash-2", 1), LATER);

        post.unpublish();

        assertThat(post.status()).isEqualTo(PostStatus.UNPUBLISHED);
        assertThat(post.firstPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.previousSlugs()).containsExactly(new Slug("winset"));
    }

    @Test
    void 다시_발행해도_처음_발행_시각은_그대로다() {
        Post post = published();
        post.unpublish();

        post.republish();

        assertThat(post.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.firstPublishedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void 내용_해시가_같으면_내용과_수정_시각을_바꾸지_않는다() {
        Post post = published();
        PostContent changed =
                new PostContent("다른 제목", new Slug("other"), "다른 본문", Summary.auto("자동"), null, "notes/윈셋.md");

        boolean revised = post.revise(changed, HASH, LATER);

        assertThat(revised).isFalse();
        assertThat(post.title()).isEqualTo("윈셋");
        assertThat(post.slug()).isEqualTo(new Slug("winset"));
        assertThat(post.contentUpdatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void 내용_해시가_다르면_내용을_바꾸고_수정_시각을_바꾼다() {
        Post post = published();
        PostContent changed = new PostContent(
                "윈셋 다시 보기", new Slug("winset"), "새 본문", Summary.auto("새 본문"), LocalDate.of(2026, 9, 1), "notes/윈셋.md");
        ContentHash newHash = new ContentHash("hash-2", 1);

        boolean revised = post.revise(changed, newHash, LATER);

        assertThat(revised).isTrue();
        assertThat(post.title()).isEqualTo("윈셋 다시 보기");
        assertThat(post.body()).isEqualTo("새 본문");
        assertThat(post.summary()).isEqualTo(Summary.auto("새 본문"));
        assertThat(post.displayDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(post.contentHash()).isEqualTo(newHash);
        assertThat(post.contentUpdatedAt()).isEqualTo(LATER);
        assertThat(post.firstPublishedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void slug가_바뀌면_옛_slug를_보관한다() {
        Post post = published();

        post.revise(content("winset-2"), new ContentHash("hash-2", 1), LATER);
        post.revise(content("winset-3"), new ContentHash("hash-3", 1), LATER);

        assertThat(post.slug()).isEqualTo(new Slug("winset-3"));
        assertThat(post.previousSlugs()).containsExactlyInAnyOrder(new Slug("winset"), new Slug("winset-2"));
    }

    @Test
    void 자기_옛_slug로_돌아가는_것은_미정이라_거부한다() {
        Post post = published();
        post.revise(content("winset-2"), new ContentHash("hash-2", 1), LATER);

        assertThatThrownBy(() -> post.revise(content("winset"), new ContentHash("hash-3", 1), LATER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미정 G");
    }

    @Test
    void 해시_방식이_다른_해시로_내용을_반영하는_것은_미정이라_거부한다() {
        Post post = published();

        assertThatThrownBy(() -> post.revise(content("winset"), new ContentHash("hash-v2", 2), LATER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미정 P");
    }

    @Test
    void 해시_방식만_바뀌면_해시만_바꾸고_수정_시각은_그대로다() {
        Post post = published();
        ContentHash v2 = new ContentHash("hash-v2", 2);

        post.rehash(v2);

        assertThat(post.contentHash()).isEqualTo(v2);
        assertThat(post.contentUpdatedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.title()).isEqualTo("윈셋");
    }

    @Test
    void 해시_방식이_같으면_rehash를_거부한다() {
        Post post = published();

        assertThatThrownBy(() -> post.rehash(new ContentHash("hash-2", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void date가_있으면_그_날짜를_화면_날짜로_쓴다() {
        PostContent dated = new PostContent(
                "윈셋", new Slug("winset"), "본문", Summary.manual("요약"), LocalDate.of(2025, 1, 2), "notes/윈셋.md");

        Post post = Post.publish(ID, dated, HASH, PUBLISHED_AT);

        assertThat(post.displayDate()).isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test
    void date가_없으면_처음_발행_시각을_한국_날짜로_바꿔_화면_날짜로_쓴다() {
        Instant utcLateNight = Instant.parse("2026-10-13T16:30:00Z"); // 한국 시간 2026-10-14 01:30

        Post post = Post.publish(ID, content("winset"), HASH, utcLateNight);

        assertThat(post.displayDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void 다시_반영할_때_date가_없으면_처음_발행_시각으로_화면_날짜를_채운다() {
        Instant utcLateNight = Instant.parse("2026-10-13T16:30:00Z");
        Post post = Post.publish(ID, content("winset"), HASH, utcLateNight);

        post.revise(content("winset"), new ContentHash("hash-2", 1), LATER);

        assertThat(post.displayDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void 내용이_바뀌면_노트_경로도_바꾼다() {
        Post post = published();
        PostContent moved =
                new PostContent("윈셋", new Slug("winset"), "새 본문", Summary.manual("요약"), null, "concepts/윈셋.md");

        post.revise(moved, new ContentHash("hash-2", 1), LATER);

        assertThat(post.sourcePath()).isEqualTo("concepts/윈셋.md");
    }

    @Test
    void 내용_해시가_같으면_노트_경로만_바뀌어도_그대로_둔다() {
        Post post = published();
        PostContent moved =
                new PostContent("윈셋", new Slug("winset"), "본문", Summary.manual("요약"), null, "concepts/윈셋.md");

        post.revise(moved, HASH, LATER);

        assertThat(post.sourcePath()).isEqualTo("notes/윈셋.md");
    }

    @Test
    void 지금_slug가_옛_slug_목록에_있으면_되살리지_않는다() {
        assertThatThrownBy(() -> Post.restore(
                        ID,
                        new Slug("winset"),
                        java.util.Set.of(new Slug("winset")),
                        "윈셋",
                        "본문",
                        Summary.manual("요약"),
                        LocalDate.of(2026, 10, 14),
                        PostStatus.PUBLISHED,
                        PUBLISHED_AT,
                        PUBLISHED_AT,
                        HASH,
                        "notes/윈셋.md"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
