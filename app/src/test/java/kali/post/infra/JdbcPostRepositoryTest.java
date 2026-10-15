package kali.post.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kali.TestcontainersConfiguration;
import kali.post.domain.ContentHash;
import kali.post.domain.Post;
import kali.post.domain.PostContent;
import kali.post.domain.PostRepository;
import kali.post.domain.PostStatus;
import kali.post.domain.Slug;
import kali.post.domain.SlugAlreadyUsedException;
import kali.post.domain.Summary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcPostRepositoryTest {

    static final Instant PUBLISHED_AT = Instant.parse("2026-10-14T03:00:00.123456Z");
    static final Instant LATER = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    PostRepository repository;

    @Autowired
    JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        jdbcClient.sql("DELETE FROM post_slug").update();
        jdbcClient.sql("DELETE FROM post").update();
    }

    static Post post(UUID id, String slug) {
        return Post.publish(
                id,
                new PostContent(
                        "윈셋",
                        new Slug(slug),
                        "본문",
                        Summary.manual("요약"),
                        LocalDate.of(2026, 10, 1),
                        "notes/" + slug + ".md"),
                new ContentHash("hash-1", 1),
                PUBLISHED_AT);
    }

    @Test
    void 저장한_글을_id로_그대로_읽는다() {
        Post saved = post(UUID.randomUUID(), "winset");

        repository.save(saved);
        Post found = repository.findById(saved.id()).orElseThrow();

        assertThat(found)
                .usingRecursiveComparison()
                .ignoringFields("previousSlugs")
                .isEqualTo(saved);
        assertThat(found.previousSlugs()).isEmpty();
    }

    @Test
    void 처음_발행_시각은_UTC_마이크로초까지_저장한다() {
        Post saved = post(UUID.randomUUID(), "winset");

        repository.save(saved);

        assertThat(repository.findById(saved.id()).orElseThrow().firstPublishedAt())
                .isEqualTo(PUBLISHED_AT);
        assertThat(jdbcClient
                        .sql("SELECT CAST(first_published_at AS CHAR) FROM post WHERE id = :id")
                        .param("id", saved.id().toString())
                        .query(String.class)
                        .single())
                .isEqualTo("2026-10-14 03:00:00.123456");
    }

    @Test
    void 발행을_취소해도_행과_처음_발행_시각이_남는다() {
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);

        post.unpublish();
        repository.save(post);

        Post found = repository.findById(post.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(PostStatus.UNPUBLISHED);
        assertThat(found.firstPublishedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void 다시_발행해도_처음_발행_시각은_그대로_저장된다() {
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);
        post.unpublish();
        repository.save(post);

        Post reloaded = repository.findById(post.id()).orElseThrow();
        reloaded.republish();
        repository.save(reloaded);

        Post found = repository.findById(post.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(found.firstPublishedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void 내용이_바뀌면_수정_시각과_해시를_저장한다() {
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);

        post.revise(
                new PostContent("새 제목", new Slug("winset"), "새 본문", Summary.auto("새 본문"), null, "notes/moved.md"),
                new ContentHash("hash-2", 1),
                LATER);
        repository.save(post);

        Post found = repository.findById(post.id()).orElseThrow();
        assertThat(found.title()).isEqualTo("새 제목");
        assertThat(found.summary()).isEqualTo(Summary.auto("새 본문"));
        assertThat(found.contentHash()).isEqualTo(new ContentHash("hash-2", 1));
        assertThat(found.contentUpdatedAt()).isEqualTo(LATER);
        assertThat(found.firstPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(found.sourcePath()).isEqualTo("notes/moved.md");
    }

    @Test
    void 해시_방식만_바뀌면_해시만_저장하고_수정_시각은_그대로다() {
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);

        post.rehash(new ContentHash("hash-v2", 2));
        repository.save(post);

        Post found = repository.findById(post.id()).orElseThrow();
        assertThat(found.contentHash()).isEqualTo(new ContentHash("hash-v2", 2));
        assertThat(found.contentUpdatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void 발행을_취소해도_옛_slug_기록이_남는다() {
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);
        post.revise(
                new PostContent("윈셋", new Slug("winset-2"), "본문", Summary.manual("요약"), null, "notes/moved.md"),
                new ContentHash("hash-2", 1),
                LATER);
        post.unpublish();
        repository.save(post);

        assertThat(repository.findBySlug(new Slug("winset")).orElseThrow().status())
                .isEqualTo(PostStatus.UNPUBLISHED);
    }

    @Test
    void slug가_바뀌면_옛_slug로도_글을_찾는다() {
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);

        post.revise(
                new PostContent("윈셋", new Slug("winset-2"), "본문", Summary.manual("요약"), null, "notes/moved.md"),
                new ContentHash("hash-2", 1),
                LATER);
        repository.save(post);

        Post byOld = repository.findBySlug(new Slug("winset")).orElseThrow();
        assertThat(byOld.id()).isEqualTo(post.id());
        assertThat(byOld.slug()).isEqualTo(new Slug("winset-2"));
        assertThat(byOld.previousSlugs()).containsExactly(new Slug("winset"));
        assertThat(repository.findBySlug(new Slug("winset-2")).orElseThrow().id())
                .isEqualTo(post.id());
    }

    @Test
    void 없는_slug로는_글을_찾지_못한다() {
        assertThat(repository.findBySlug(new Slug("nothing"))).isEmpty();
    }

    @Test
    void 다른_글이_지금_쓰는_slug는_쓸_수_없고_아무것도_저장하지_않는다() {
        repository.save(post(UUID.randomUUID(), "winset"));
        Post other = post(UUID.randomUUID(), "winset");

        assertThatThrownBy(() -> repository.save(other))
                .isInstanceOf(SlugAlreadyUsedException.class)
                .extracting(e -> ((SlugAlreadyUsedException) e).slug())
                .isEqualTo(new Slug("winset"));
        assertThat(repository.findById(other.id())).isEmpty();
    }

    @Test
    void 다른_글이_예전에_쓴_slug는_쓸_수_없다() {
        Post first = post(UUID.randomUUID(), "winset");
        repository.save(first);
        first.revise(
                new PostContent("윈셋", new Slug("winset-2"), "본문", Summary.manual("요약"), null, "notes/moved.md"),
                new ContentHash("hash-2", 1),
                LATER);
        repository.save(first);

        Post other = post(UUID.randomUUID(), "winset");

        assertThatThrownBy(() -> repository.save(other)).isInstanceOf(SlugAlreadyUsedException.class);
        assertThat(repository.findBySlug(new Slug("winset")).orElseThrow().id()).isEqualTo(first.id());
    }

    @Test
    void 있는_글의_slug를_다른_글의_slug로_바꾸면_거부하고_원래대로_둔다() {
        repository.save(post(UUID.randomUUID(), "taken"));
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);

        post.revise(
                new PostContent("윈셋", new Slug("taken"), "본문", Summary.manual("요약"), null, "notes/moved.md"),
                new ContentHash("hash-2", 1),
                LATER);

        assertThatThrownBy(() -> repository.save(post)).isInstanceOf(SlugAlreadyUsedException.class);
        Post found = repository.findById(post.id()).orElseThrow();
        assertThat(found.slug()).isEqualTo(new Slug("winset"));
        assertThat(found.contentHash()).isEqualTo(new ContentHash("hash-1", 1));
    }

    // post_slug에 기록 없이 post에만 slug가 있는 상태. 사전 확인을 지나쳐 DB 유일 제약에서 걸리는 경우를 만든다
    void slug_기록_없이_글_행만_넣는다(String slug) {
        jdbcClient
                .sql("""
                        INSERT INTO post (id, slug, title, body, summary, summary_source, status, display_date,
                                          first_published_at, content_updated_at, content_hash, content_hash_version,
                                          source_path)
                        VALUES (:id, :slug, '다른 글', '', '', 'AUTO', 'PUBLISHED', '2026-10-01',
                                '2026-10-01 00:00:00', '2026-10-01 00:00:00', 'h', 1, 'notes/raw.md')
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("slug", slug)
                .update();
    }

    @Test
    void 새_글이_DB_유일_제약에_걸리면_slug_예외로_바꾸고_저장하지_않는다() {
        slug_기록_없이_글_행만_넣는다("raw");
        Post other = post(UUID.randomUUID(), "raw");

        assertThatThrownBy(() -> repository.save(other))
                .isInstanceOf(SlugAlreadyUsedException.class)
                .hasCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(repository.findById(other.id())).isEmpty();
    }

    @Test
    void 있는_글의_수정이_DB_유일_제약에_걸리면_slug_예외로_바꾸고_원래대로_둔다() {
        Post post = post(UUID.randomUUID(), "winset");
        repository.save(post);
        slug_기록_없이_글_행만_넣는다("raw");

        post.revise(
                new PostContent("윈셋", new Slug("raw"), "본문", Summary.manual("요약"), null, "notes/moved.md"),
                new ContentHash("hash-2", 1),
                LATER);

        assertThatThrownBy(() -> repository.save(post))
                .isInstanceOf(SlugAlreadyUsedException.class)
                .hasCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        Post found = repository.findById(post.id()).orElseThrow();
        assertThat(found.slug()).isEqualTo(new Slug("winset"));
        assertThat(found.contentHash()).isEqualTo(new ContentHash("hash-1", 1));
        assertThat(found.previousSlugs()).isEmpty();
    }
}
