package kali.sync.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kali.post.domain.Post;
import kali.post.domain.PostRepository;
import kali.post.domain.Slug;
import kali.post.domain.SlugAlreadyUsedException;

/** Docker 없이 동기화 규칙을 검사하려는 메모리 저장소. slug 유일은 JdbcPostRepository와 같게 지킨다. 저장한 글은 복사해 둔다. */
class InMemoryPostRepository implements PostRepository {

    private final Map<UUID, Post> posts = new LinkedHashMap<>();
    private final Map<Slug, UUID> slugOwners = new LinkedHashMap<>();
    int saves;

    @Override
    public void save(Post post) {
        List<Slug> slugs = new ArrayList<>(post.previousSlugs());
        slugs.add(post.slug());
        for (Slug slug : slugs) {
            UUID owner = slugOwners.get(slug);
            if (owner != null && !owner.equals(post.id())) {
                throw new SlugAlreadyUsedException(slug, null);
            }
        }
        slugs.forEach(slug -> slugOwners.put(slug, post.id()));
        posts.put(post.id(), copy(post));
        saves++;
    }

    @Override
    public Optional<Post> findById(UUID id) {
        return Optional.ofNullable(posts.get(id)).map(InMemoryPostRepository::copy);
    }

    @Override
    public List<Post> findAll() {
        return posts.values().stream().map(InMemoryPostRepository::copy).toList();
    }

    @Override
    public Optional<Post> findBySlug(Slug slug) {
        return Optional.ofNullable(slugOwners.get(slug)).flatMap(this::findById);
    }

    private static Post copy(Post post) {
        return Post.restore(
                post.id(),
                post.slug(),
                post.previousSlugs(),
                post.title(),
                post.body(),
                post.summary(),
                post.displayDate(),
                post.status(),
                post.firstPublishedAt(),
                post.contentUpdatedAt(),
                post.contentHash(),
                post.sourcePath(),
                post.tags());
    }
}
