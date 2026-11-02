package kali.sync.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import kali.post.domain.ContentHash;
import kali.post.domain.PostContent;
import kali.post.domain.Summary;
import kali.post.domain.Tag;

/** 내용 해시 계산 (스펙 5. 발행의 다시 발행할 때). */
public final class ContentHasher {

    /**
     * 해시 방식 버전. 해시에 넣는 값, 계산 방식, 저장할 본문을 만드는 방식(이미지 저장소의 종류·주소 포함) 중 하나라도 바꾸면 올린다. 올리면
     * 다음 동기화가 모든 글의 내용을 다시 쓰고 수정 시각은 그대로 둔다.
     */
    public static final int VERSION = 1;

    private ContentHasher() {}

    /**
     * 본문(이미지 경로를 바꾸기 전), 제목, slug, 이름순 태그, 직접 쓴 요약, 화면 날짜를 넣는다. 값마다 길이를 앞에 붙여 경계가 섞이지 않게 한다.
     *
     * @param displayDate 없을 때 처음 발행 시각으로 채운 뒤의 화면 날짜
     */
    public static ContentHash hash(PostDraft draft, LocalDate displayDate) {
        PostContent content = draft.content();
        StringBuilder in = new StringBuilder();
        field(in, "body", draft.originalBody());
        field(in, "title", content.title());
        field(in, "slug", content.slug().value());
        field(in, "tags", String.valueOf(content.tags().size()));
        for (Tag tag : content.tags()) {
            field(in, "tag", tag.name());
        }
        Summary summary = content.summary();
        field(in, "summary", summary.source() == Summary.Source.MANUAL ? "manual:" + summary.text() : "none");
        field(in, "date", displayDate.toString());
        return new ContentHash(sha256(in.toString()), VERSION);
    }

    private static void field(StringBuilder in, String name, String value) {
        in.append(name)
                .append(':')
                .append(value.length())
                .append(':')
                .append(value)
                .append('\n');
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
