package kali.sync.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kali.post.domain.ContentHash;
import kali.post.domain.PostContent;
import kali.post.domain.Slug;
import kali.post.domain.Summary;
import kali.post.domain.Tag;
import org.junit.jupiter.api.Test;

class ContentHasherTest {

    static final LocalDate DATE = LocalDate.of(2026, 8, 27);

    static PostDraft draft(
            String title, String slug, String storedBody, String originalBody, Summary summary, List<Tag> tags) {
        return new PostDraft(
                UUID.randomUUID(),
                new PostContent(title, new Slug(slug), storedBody, summary, null, "a.md", tags),
                originalBody,
                Map.of());
    }

    static PostDraft base() {
        return draft("윈셋", "winset", "본문", "본문", Summary.manual("요약"), List.of(new Tag("db")));
    }

    static ContentHash hash(PostDraft draft) {
        return ContentHasher.hash(draft, DATE);
    }

    @Test
    void 같은_내용이면_같은_해시이고_지금_방식_버전을_붙인다() {
        assertThat(hash(base())).isEqualTo(hash(base()));
        assertThat(hash(base()).version()).isEqualTo(ContentHasher.VERSION);
    }

    @Test
    void 해시에_넣는_값이_하나라도_바뀌면_해시가_바뀐다() {
        ContentHash base = hash(base());

        assertThat(hash(draft("다른", "winset", "본문", "본문", Summary.manual("요약"), List.of(new Tag("db")))))
                .isNotEqualTo(base);
        assertThat(hash(draft("윈셋", "other", "본문", "본문", Summary.manual("요약"), List.of(new Tag("db")))))
                .isNotEqualTo(base);
        assertThat(hash(draft("윈셋", "winset", "본문", "다른", Summary.manual("요약"), List.of(new Tag("db")))))
                .isNotEqualTo(base);
        assertThat(hash(draft("윈셋", "winset", "본문", "본문", Summary.manual("다른"), List.of(new Tag("db")))))
                .isNotEqualTo(base);
        assertThat(hash(draft("윈셋", "winset", "본문", "본문", Summary.manual("요약"), List.of())))
                .isNotEqualTo(base);
        assertThat(ContentHasher.hash(base(), DATE.plusDays(1))).isNotEqualTo(base);
    }

    @Test
    void 이미지_경로를_바꾼_저장용_본문은_해시에_넣지_않는다() {
        assertThat(hash(draft("윈셋", "winset", "다른 주소", "본문", Summary.manual("요약"), List.of(new Tag("db")))))
                .isEqualTo(hash(base()));
    }

    @Test
    void 자동_요약은_해시에_넣지_않는다() {
        assertThat(hash(draft("윈셋", "winset", "본문", "본문", Summary.auto("하나"), List.of())))
                .isEqualTo(hash(draft("윈셋", "winset", "본문", "본문", Summary.auto("둘"), List.of())));
    }

    @Test
    void 값의_경계가_섞여도_해시가_다르다() {
        assertThat(hash(draft("ab", "winset", "c", "c", Summary.auto(""), List.of())))
                .isNotEqualTo(hash(draft("a", "winset", "bc", "bc", Summary.auto(""), List.of())));
    }
}
