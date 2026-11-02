package kali.post.domain;

import java.util.Objects;

/** 글 제목 정하기 (스펙 6. 글 속성). */
public final class PostTitle {

    private PostTitle() {}

    /**
     * frontmatter {@code title}이 없거나 비어 있거나 공백만 있으면 파일 이름(확장자 제외)을 제목으로 쓴다. 앞뒤 공백은 지운다.
     * {@code title}이 문자열이 아닌 노트는 동기화가 건너뛴다.
     */
    public static String resolve(String frontmatterTitle, String sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        if (frontmatterTitle == null || frontmatterTitle.isBlank()) {
            return fileNameWithoutExtension(sourcePath).strip();
        }
        return frontmatterTitle.strip();
    }

    private static String fileNameWithoutExtension(String sourcePath) {
        String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
