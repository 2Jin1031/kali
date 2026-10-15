package kali.post.domain;

/** 다른 글이 지금 쓰거나 예전에 쓴 slug를 쓰려 할 때 (스펙 3. 글 식별). */
public class SlugAlreadyUsedException extends RuntimeException {

    private final Slug slug;

    public SlugAlreadyUsedException(Slug slug, Throwable cause) {
        super("다른 글이 지금 쓰거나 예전에 쓴 slug다: " + slug.value(), cause);
        this.slug = slug;
    }

    public Slug slug() {
        return slug;
    }
}
