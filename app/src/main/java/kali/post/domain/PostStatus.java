package kali.post.domain;

/** DB에 있는 글의 상태. 초안은 옵시디언에만 있고 DB에 들어오지 않는다 (스펙 4. 글 상태). */
public enum PostStatus {
    PUBLISHED,
    UNPUBLISHED
}
