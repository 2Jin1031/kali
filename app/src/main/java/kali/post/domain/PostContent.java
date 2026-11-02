package kali.post.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 동기화가 노트에서 읽어 글에 넣는 내용. 칸 길이 제한을 넘으면 만들지 않는다 (스펙 6. 글 속성의 길이 제한).
 *
 * @param title 빈 값을 채운 뒤의 제목 ({@link PostTitle#resolve})
 * @param date frontmatter {@code date}의 날짜 부분. 없으면 null (처음 발행 시각으로 채운다)
 * @param sourcePath 노트 파일 경로. 식별자가 아니고 내용 해시에도 넣지 않는다
 */
public record PostContent(String title, Slug slug, String body, Summary summary, LocalDate date, String sourcePath) {

    public PostContent {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("제목은 비어 있을 수 없다");
        }
        Lengths.requireChars("제목", title, Lengths.TITLE_CHARS);
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(body, "body");
        Lengths.requireUtf8Bytes("본문", body, Lengths.BODY_BYTES);
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Lengths.requireChars("노트 경로", sourcePath, Lengths.SOURCE_PATH_CHARS);
    }
}
