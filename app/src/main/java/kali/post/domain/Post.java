package kali.post.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 옵시디언에서 발행한 글 (스펙 3~6절).
 *
 * <p>상태 변화는 동기화가 부른다: 처음 발행은 {@link #publish}, 발행 취소는 {@link #unpublish}, 다시 발행은
 * {@link #republish}, 내용 반영은 {@link #revise}, 해시 방식만 바뀐 경우는 {@link #rehash}.
 */
public class Post {

    /** 화면 날짜가 없을 때 처음 발행 시각을 날짜로 바꾸는 시간대 (스펙 6. 글 속성의 발행일). */
    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private final UUID id;
    private final Instant firstPublishedAt;
    private final Set<Slug> previousSlugs;
    private String sourcePath;
    private Slug slug;
    private String title;
    private String body;
    private Summary summary;
    private LocalDate displayDate;
    private PostStatus status;
    private Instant contentUpdatedAt;
    private ContentHash contentHash;

    private Post(
            UUID id,
            Slug slug,
            Set<Slug> previousSlugs,
            String title,
            String body,
            Summary summary,
            LocalDate displayDate,
            PostStatus status,
            Instant firstPublishedAt,
            Instant contentUpdatedAt,
            ContentHash contentHash,
            String sourcePath) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.previousSlugs = new LinkedHashSet<>(previousSlugs);
        this.title = Objects.requireNonNull(title, "title");
        this.body = Objects.requireNonNull(body, "body");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.displayDate = Objects.requireNonNull(displayDate, "displayDate");
        this.status = Objects.requireNonNull(status, "status");
        this.firstPublishedAt = Objects.requireNonNull(firstPublishedAt, "firstPublishedAt");
        this.contentUpdatedAt = Objects.requireNonNull(contentUpdatedAt, "contentUpdatedAt");
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        if (this.previousSlugs.contains(slug)) {
            throw new IllegalArgumentException("지금 slug가 옛 slug 목록에 있다: " + slug.value());
        }
    }

    /** 처음 발행. 처음 발행 시각은 이때 정해지고 바뀌지 않는다. */
    public static Post publish(UUID id, PostContent content, ContentHash hash, Instant now) {
        return new Post(
                id,
                content.slug(),
                Set.of(),
                content.title(),
                content.body(),
                content.summary(),
                displayDate(content.date(), now),
                PostStatus.PUBLISHED,
                now,
                now,
                hash,
                content.sourcePath());
    }

    /** 저장소에서 읽은 값으로 글을 되살린다. */
    public static Post restore(
            UUID id,
            Slug slug,
            Set<Slug> previousSlugs,
            String title,
            String body,
            Summary summary,
            LocalDate displayDate,
            PostStatus status,
            Instant firstPublishedAt,
            Instant contentUpdatedAt,
            ContentHash contentHash,
            String sourcePath) {
        return new Post(
                id,
                slug,
                previousSlugs,
                title,
                body,
                summary,
                displayDate,
                status,
                firstPublishedAt,
                contentUpdatedAt,
                contentHash,
                sourcePath);
    }

    /** frontmatter {@code date}가 없으면 처음 발행 시각을 한국 시간으로 바꾼 날짜를 쓴다. */
    public static LocalDate displayDate(LocalDate frontmatterDate, Instant firstPublishedAt) {
        return frontmatterDate != null ? frontmatterDate : LocalDate.ofInstant(firstPublishedAt, DISPLAY_ZONE);
    }

    /** 발행 취소. 행과 처음 발행 시각, 옛 slug 기록은 그대로 남는다. */
    public void unpublish() {
        status = PostStatus.UNPUBLISHED;
    }

    /** 다시 발행. 내용이 같아도 상태는 바뀌고, 처음 발행 시각은 그대로다. */
    public void republish() {
        status = PostStatus.PUBLISHED;
    }

    /**
     * 내용을 반영한다. 내용 해시가 같으면 아무것도 바꾸지 않는다 (해시에 넣지 않는 노트 경로만 바뀐 경우의 반영은 스펙 미정 S).
     * 다르면 내용과 노트 경로를 바꾸고 수정 시각을 {@code now}로 한다.
     *
     * @return 내용을 바꿨으면 true
     * @throws IllegalStateException 해시 방식 버전이 저장된 것과 다를 때. 이때 내용도 바뀐 글의 처리는 스펙 미정 P다
     */
    public boolean revise(PostContent content, ContentHash hash, Instant now) {
        if (!contentHash.sameVersion(hash)) {
            throw new IllegalStateException("해시 방식이 바뀐 동기화에서 내용을 반영하는 방법은 스펙 미정 P다. 해시만 바꾸려면 rehash를 쓴다");
        }
        if (contentHash.equals(hash)) {
            return false;
        }
        changeSlug(content.slug());
        title = content.title();
        body = content.body();
        summary = content.summary();
        displayDate = displayDate(content.date(), firstPublishedAt);
        sourcePath = content.sourcePath();
        contentHash = hash;
        contentUpdatedAt = now;
        return true;
    }

    /** 해시 방식만 바뀐 경우. 해시만 바꾸고 수정 시각은 그대로 둔다. */
    public void rehash(ContentHash hash) {
        if (contentHash.sameVersion(hash)) {
            throw new IllegalArgumentException("해시 방식 버전이 같으면 revise로 내용을 비교한다");
        }
        contentHash = hash;
    }

    private void changeSlug(Slug newSlug) {
        if (slug.equals(newSlug)) {
            return;
        }
        if (previousSlugs.contains(newSlug)) {
            throw new IllegalStateException("글이 자기 옛 slug로 돌아가는 경우의 처리는 스펙 미정 G다: " + newSlug.value());
        }
        previousSlugs.add(slug);
        slug = newSlug;
    }

    public UUID id() {
        return id;
    }

    public Slug slug() {
        return slug;
    }

    /** 이 글이 예전에 쓴 slug. 옛 URL을 지금 URL로 리다이렉트할 때 쓴다. */
    public Set<Slug> previousSlugs() {
        return Set.copyOf(previousSlugs);
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public Summary summary() {
        return summary;
    }

    public LocalDate displayDate() {
        return displayDate;
    }

    public PostStatus status() {
        return status;
    }

    public Instant firstPublishedAt() {
        return firstPublishedAt;
    }

    /** 내용을 마지막으로 쓴 시각. 처음 발행 때 처음 발행 시각과 같고, 내용 해시가 바뀔 때만 바뀐다. */
    public Instant contentUpdatedAt() {
        return contentUpdatedAt;
    }

    public ContentHash contentHash() {
        return contentHash;
    }

    /** 마지막으로 내용을 반영한 노트의 경로. 식별자가 아니다. */
    public String sourcePath() {
        return sourcePath;
    }
}
