package kali.sync.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kali.post.domain.ContentHash;
import kali.post.domain.Post;
import kali.post.domain.PostRepository;
import kali.post.domain.PostStatus;
import kali.post.domain.Slug;
import kali.sync.domain.ContentHasher;
import kali.sync.domain.ImageResolver;
import kali.sync.domain.Note;
import kali.sync.domain.NoteParser;
import kali.sync.domain.NoteSkippedException;
import kali.sync.domain.PostDraft;
import kali.sync.domain.SkippedNote;
import kali.sync.domain.SyncReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 볼트를 읽어 DB의 글을 맞춘다: 발행, 다시 발행(수정), 발행 취소 (스펙 5. 발행). 링크(7절)는 아직 만들지 않는다.
 *
 * <p>이미지 저장소 구현이 아직 없어(인프라 단계) {@code kali.sync.enabled=true}일 때만 만든다.
 */
@Service
@ConditionalOnProperty(name = "kali.sync.enabled", havingValue = "true")
public class PostSyncService {

    private final PostRepository posts;
    private final ImageStore imageStore;
    private final Clock clock;

    @Autowired
    public PostSyncService(PostRepository posts, ImageStore imageStore) {
        this(posts, imageStore, Clock.systemUTC());
    }

    public PostSyncService(PostRepository posts, ImageStore imageStore, Clock clock) {
        this.posts = posts;
        this.imageStore = imageStore;
        this.clock = clock;
    }

    /**
     * 처음 발행 시각은 이 동기화 한 번에 한 시각으로 찍는다.
     *
     * @throws NoPublishTargetException 발행 대상 노트가 0개일 때. 이때 아무것도 바꾸지 않는다
     */
    @Transactional
    public SyncReport sync(Vault vault) {
        Report report = new Report();
        List<Note> notes = vault.notes();
        List<Note> targets = notes.stream().filter(Note::isPublishTarget).toList();
        if (targets.isEmpty()) {
            throw new NoPublishTargetException();
        }
        // frontmatter를 읽지 못한 노트는 발행 대상인지 알 수 없다. publish: true로 보이는 줄이 있으면 알린다
        notes.stream()
                .filter(note -> note.unreadableReason().isPresent() && looksPublished(note))
                .forEach(note -> report.skip(note.path(), "frontmatter를 YAML로 읽지 못함: " + note.frontmatterError()));

        Set<UUID> kept = new HashSet<>(); // DB에서 발행 취소하지 않을 글
        NoteParser parser = new NoteParser(new ImageResolver(vault.files()), imageStore::urlOf);
        List<PostDraft> drafts = new ArrayList<>();
        for (Note note : targets) {
            try {
                drafts.add(parser.parse(note));
            } catch (NoteSkippedException e) {
                report.skip(note.path(), e.getMessage());
                e.id().ifPresent(kept::add);
            }
        }
        drafts = withoutDuplicateIds(drafts, report, kept);
        drafts = withoutSameSyncSlugs(drafts, report, kept);
        drafts = withoutOtherPostsSlugs(drafts, report, kept);

        Instant now = clock.instant();
        for (PostDraft draft : drafts) {
            kept.add(draft.id());
            apply(vault, draft, now, report);
        }
        for (Post post : posts.findAll()) {
            if (post.status() == PostStatus.PUBLISHED && !kept.contains(post.id())) {
                post.unpublish();
                posts.save(post);
                report.unpublished.add(post.slug().value());
            }
        }
        return report.build();
    }

    private void apply(Vault vault, PostDraft draft, Instant now, Report report) {
        Optional<Post> existing = posts.findById(draft.id());
        if (existing.isEmpty()) {
            ContentHash hash =
                    ContentHasher.hash(draft, Post.displayDate(draft.content().date(), now));
            upload(vault, draft);
            posts.save(Post.publish(draft.id(), draft.content(), hash, now));
            report.published.add(draft.sourcePath());
            return;
        }
        Post post = existing.get();
        ContentHash hash =
                ContentHasher.hash(draft, Post.displayDate(draft.content().date(), post.firstPublishedAt()));
        boolean wasUnpublished = post.status() == PostStatus.UNPUBLISHED;
        boolean rehash = post.contentHash().version() != hash.version();
        boolean moved = !post.sourcePath().equals(draft.sourcePath());
        // 이미지는 내용을 다시 쓸 때만 올린다. 이름이 같고 파일만 바뀐 이미지는 본문이 바뀌기 전까지 반영하지 않는다 (스펙 5. 발행)
        if (!post.contentHash().equals(hash)) {
            upload(vault, draft);
        }
        boolean rewritten = post.revise(draft.content(), hash, now);
        if (wasUnpublished) {
            post.republish();
            report.republished.add(draft.sourcePath());
        }
        if (rewritten || wasUnpublished || moved) {
            posts.save(post);
        }
        if (rewritten) {
            (rehash ? report.rehashed : report.revised).add(draft.sourcePath());
        } else if (moved) {
            report.moved.add(draft.sourcePath());
        } else if (!wasUnpublished) {
            report.unchanged.add(draft.sourcePath());
        }
    }

    private void upload(Vault vault, PostDraft draft) {
        draft.images().forEach((key, path) -> imageStore.put(key, vault.read(path)));
    }

    // 같은 id를 가진 노트가 둘 이상이면 모두 건너뛴다
    private static List<PostDraft> withoutDuplicateIds(List<PostDraft> drafts, Report report, Set<UUID> kept) {
        Map<UUID, List<PostDraft>> byId =
                drafts.stream().collect(Collectors.groupingBy(PostDraft::id, LinkedHashMap::new, Collectors.toList()));
        List<PostDraft> result = new ArrayList<>();
        byId.forEach((id, group) -> {
            if (group.size() == 1) {
                result.add(group.getFirst());
                return;
            }
            kept.add(id);
            String paths = group.stream().map(PostDraft::sourcePath).collect(Collectors.joining(", "));
            group.forEach(draft -> report.skip(draft.sourcePath(), "같은 id를 가진 노트가 둘 이상: " + paths));
        });
        return result;
    }

    // 같은 동기화에서 slug가 겹치면 그 slug를 이미 가진 글의 노트만 남긴다 (스펙 3. 글 식별)
    private List<PostDraft> withoutSameSyncSlugs(List<PostDraft> drafts, Report report, Set<UUID> kept) {
        Map<Slug, List<PostDraft>> bySlug = drafts.stream()
                .collect(Collectors.groupingBy(d -> d.content().slug(), LinkedHashMap::new, Collectors.toList()));
        List<PostDraft> result = new ArrayList<>();
        bySlug.forEach((slug, group) -> {
            if (group.size() == 1) {
                result.add(group.getFirst());
                return;
            }
            Optional<UUID> owner = posts.findBySlug(slug).map(Post::id);
            String paths = group.stream().map(PostDraft::sourcePath).collect(Collectors.joining(", "));
            for (PostDraft draft : group) {
                if (owner.isPresent() && owner.get().equals(draft.id())) {
                    result.add(draft);
                } else {
                    kept.add(draft.id());
                    report.skip(draft.sourcePath(), "같은 동기화의 다른 노트와 slug가 겹침: " + slug.value() + " (" + paths + ")");
                }
            }
        });
        return result;
    }

    private List<PostDraft> withoutOtherPostsSlugs(List<PostDraft> drafts, Report report, Set<UUID> kept) {
        List<PostDraft> result = new ArrayList<>();
        for (PostDraft draft : drafts) {
            Slug slug = draft.content().slug();
            Optional<UUID> owner = posts.findBySlug(slug).map(Post::id);
            if (owner.isPresent() && !owner.get().equals(draft.id())) {
                kept.add(draft.id());
                report.skip(draft.sourcePath(), "다른 글이 지금 쓰거나 예전에 쓴 slug: " + slug.value());
            } else {
                result.add(draft);
            }
        }
        return result;
    }

    private static boolean looksPublished(Note note) {
        return note.body().lines().anyMatch(line -> line.strip().matches("publish:\\s*true"));
    }

    private static final class Report {
        final List<String> published = new ArrayList<>();
        final List<String> republished = new ArrayList<>();
        final List<String> revised = new ArrayList<>();
        final List<String> rehashed = new ArrayList<>();
        final List<String> moved = new ArrayList<>();
        final List<String> unchanged = new ArrayList<>();
        final List<String> unpublished = new ArrayList<>();
        final List<SkippedNote> skipped = new ArrayList<>();

        void skip(String path, String reason) {
            skipped.add(new SkippedNote(path, reason));
        }

        SyncReport build() {
            return new SyncReport(published, republished, revised, rehashed, moved, unchanged, unpublished, skipped);
        }
    }
}
