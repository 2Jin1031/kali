package kali.post.domain;

import java.util.Objects;

/** 글 제목 정하기 (스펙 6. 글 속성). */
public final class PostTitle {

    private PostTitle() {}

    /**
     * frontmatter {@code title}이 없거나 비어 있으면 파일 이름(확장자 제외)을 제목으로 쓴다.
     *
     * @throws IllegalArgumentException {@code title}이 공백만 있을 때. 이 경우의 처리는 스펙 미정 L이다
     */
    public static String resolve(String frontmatterTitle, String sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        if (frontmatterTitle == null || frontmatterTitle.isEmpty()) {
            return fileNameWithoutExtension(sourcePath);
        }
        if (frontmatterTitle.isBlank()) {
            throw new IllegalArgumentException("공백만 있는 title의 처리는 스펙 미정 L이다: " + sourcePath);
        }
        return frontmatterTitle;
    }

    private static String fileNameWithoutExtension(String sourcePath) {
        String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
