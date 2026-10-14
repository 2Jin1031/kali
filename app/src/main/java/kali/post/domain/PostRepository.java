package kali.post.domain;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    /**
     * 새 글이면 추가하고, 있으면 덮어쓴다. 지금 slug와 옛 slug를 모두 이 글의 것으로 기록한다.
     *
     * @throws SlugAlreadyUsedException 다른 글이 지금 쓰거나 예전에 쓴 slug일 때. 이때 아무것도 저장하지 않는다
     */
    void save(Post post);

    Optional<Post> findById(UUID id);

    /** 지금 slug나 옛 slug로 글을 찾는다. 옛 slug로 찾았는지는 {@link Post#slug()}와 비교해 안다. */
    Optional<Post> findBySlug(Slug slug);
}
