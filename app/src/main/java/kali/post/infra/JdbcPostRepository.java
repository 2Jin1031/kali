package kali.post.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kali.post.domain.ContentHash;
import kali.post.domain.Post;
import kali.post.domain.PostRepository;
import kali.post.domain.PostStatus;
import kali.post.domain.Slug;
import kali.post.domain.SlugAlreadyUsedException;
import kali.post.domain.Summary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 글을 {@code post}, {@code post_slug} 테이블에 저장한다 (db/migration/V1__create_post.sql). 시각은 UTC로 저장한다. */
@Repository
public class JdbcPostRepository implements PostRepository {

    private final JdbcClient jdbcClient;

    public JdbcPostRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void save(Post post) {
        String id = post.id().toString();
        List<Slug> slugs = new ArrayList<>();
        slugs.add(post.slug());
        slugs.addAll(post.previousSlugs());
        for (Slug slug : slugs) {
            Optional<String> owner = slugOwner(slug);
            if (owner.isPresent() && !owner.get().equals(id)) {
                throw new SlugAlreadyUsedException(slug, null);
            }
        }
        try {
            if (exists(id)) {
                update(post);
            } else {
                insert(post);
            }
            for (Slug slug : slugs) {
                if (slugOwner(slug).isEmpty()) {
                    jdbcClient
                            .sql("INSERT INTO post_slug (slug, post_id) VALUES (:slug, :postId)")
                            .param("slug", slug.value())
                            .param("postId", id)
                            .update();
                }
            }
        } catch (DuplicateKeyException e) {
            // 위 확인과 쓰기 사이에 다른 저장이 같은 slug를 가져간 경우
            throw new SlugAlreadyUsedException(post.slug(), e);
        }
    }

    @Override
    public Optional<Post> findById(UUID id) {
        // 옛 slug를 먼저 읽고 글 행을 읽는다 (RowMapper 안에서 다시 조회하면 커넥션을 두 개 쥔다)
        List<Slug> slugs = jdbcClient
                .sql("SELECT slug FROM post_slug WHERE post_id = :postId ORDER BY slug")
                .param("postId", id.toString())
                .query((rs, rowNum) -> new Slug(rs.getString("slug")))
                .list();
        return jdbcClient
                .sql("SELECT * FROM post WHERE id = :id")
                .param("id", id.toString())
                .query((rs, rowNum) -> toPost(rs, slugs))
                .optional();
    }

    @Override
    public Optional<Post> findBySlug(Slug slug) {
        return slugOwner(slug).map(UUID::fromString).flatMap(this::findById);
    }

    private Optional<String> slugOwner(Slug slug) {
        return jdbcClient
                .sql("SELECT post_id FROM post_slug WHERE slug = :slug")
                .param("slug", slug.value())
                .query(String.class)
                .optional();
    }

    private boolean exists(String id) {
        return jdbcClient
                        .sql("SELECT COUNT(*) FROM post WHERE id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single()
                > 0;
    }

    private void insert(Post post) {
        jdbcClient.sql("""
                        INSERT INTO post (id, slug, title, body, summary, summary_source, status, display_date,
                                          first_published_at, content_updated_at, content_hash, content_hash_version,
                                          source_path)
                        VALUES (:id, :slug, :title, :body, :summary, :summarySource, :status, :displayDate,
                                :firstPublishedAt, :contentUpdatedAt, :contentHash, :contentHashVersion, :sourcePath)
                        """).params(columns(post)).update();
    }

    private void update(Post post) {
        // first_published_at은 처음 저장한 값에서 바꾸지 않는다
        jdbcClient.sql("""
                        UPDATE post
                           SET slug = :slug, title = :title, body = :body, summary = :summary,
                               summary_source = :summarySource, status = :status, display_date = :displayDate,
                               content_updated_at = :contentUpdatedAt, content_hash = :contentHash,
                               content_hash_version = :contentHashVersion, source_path = :sourcePath
                         WHERE id = :id
                        """).params(columns(post)).update();
    }

    private static Map<String, Object> columns(Post post) {
        return Map.ofEntries(
                Map.entry("id", post.id().toString()),
                Map.entry("slug", post.slug().value()),
                Map.entry("title", post.title()),
                Map.entry("body", post.body()),
                Map.entry("summary", post.summary().text()),
                Map.entry("summarySource", post.summary().source().name()),
                Map.entry("status", post.status().name()),
                Map.entry("displayDate", post.displayDate()),
                Map.entry("firstPublishedAt", utc(post.firstPublishedAt())),
                Map.entry("contentUpdatedAt", utc(post.contentUpdatedAt())),
                Map.entry("contentHash", post.contentHash().value()),
                Map.entry("contentHashVersion", post.contentHash().version()),
                Map.entry("sourcePath", post.sourcePath()));
    }

    private static Post toPost(ResultSet rs, List<Slug> slugs) throws SQLException {
        Slug slug = new Slug(rs.getString("slug"));
        Set<Slug> previousSlugs = new LinkedHashSet<>(slugs);
        previousSlugs.remove(slug);
        return Post.restore(
                UUID.fromString(rs.getString("id")),
                slug,
                previousSlugs,
                rs.getString("title"),
                rs.getString("body"),
                new Summary(rs.getString("summary"), Summary.Source.valueOf(rs.getString("summary_source"))),
                rs.getObject("display_date", LocalDate.class),
                PostStatus.valueOf(rs.getString("status")),
                instant(rs.getObject("first_published_at", LocalDateTime.class)),
                instant(rs.getObject("content_updated_at", LocalDateTime.class)),
                new ContentHash(rs.getString("content_hash"), rs.getInt("content_hash_version")),
                rs.getString("source_path"));
    }

    // DATETIME(6)에 드라이버·서버 시간대와 상관없이 UTC 그대로 넣고 읽는다
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC);
    }
}
