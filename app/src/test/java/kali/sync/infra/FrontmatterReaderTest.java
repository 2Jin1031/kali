package kali.sync.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kali.sync.domain.Note;
import org.junit.jupiter.api.Test;

class FrontmatterReaderTest {

    @Test
    void frontmatter와_본문을_나누고_날짜는_글자_그대로_둔다() {
        Note note = FrontmatterReader.read(
                "a.md",
                "---\nid: c97535fc-f85c-47c7-8d47-67675b1a3bcf\ntitle: \"[초록] 밋업\"\ndate: 2026-08-27\n"
                        + "tags:\n  - 초록\npublish: true\n---\n\n## 본문\n");

        assertThat(note.unreadableReason()).isEmpty();
        assertThat(note.frontmatter())
                .containsEntry("title", "[초록] 밋업")
                .containsEntry("date", "2026-08-27")
                .containsEntry("tags", List.of("초록"))
                .containsEntry("publish", true);
        assertThat(note.isPublishTarget()).isTrue();
        assertThat(note.body()).isEqualTo("\n## 본문\n");
    }

    @Test
    void 시각이_든_날짜와_템플릿_자리도_글자_그대로다() {
        Note note =
                FrontmatterReader.read("a.md", "---\ndate: 2026-08-27 10:00:00\ncreated: 2026-10-14T10:00:00\n---\n");

        assertThat(note.frontmatter())
                .containsEntry("date", "2026-08-27 10:00:00")
                .containsEntry("created", "2026-10-14T10:00:00");
    }

    @Test
    void 빈_값은_null이다() {
        Note note = FrontmatterReader.read("a.md", "---\ntitle:\ntags:\n---\n본문");

        assertThat(note.frontmatter()).containsEntry("title", null).containsEntry("tags", null);
    }

    @Test
    void frontmatter가_없으면_전체가_본문이다() {
        Note note = FrontmatterReader.read("a.md", "# 제목\n---\n본문");

        assertThat(note.frontmatter()).isEmpty();
        assertThat(note.body()).isEqualTo("# 제목\n---\n본문");
        assertThat(note.isPublishTarget()).isFalse();
    }

    @Test
    void CRLF와_BOM이_있어도_읽는다() {
        Note note = FrontmatterReader.read("a.md", "﻿---\r\npublish: true\r\n---\r\n본문");

        assertThat(note.isPublishTarget()).isTrue();
        assertThat(note.body()).isEqualTo("본문");
    }

    @Test
    void publish가_문자열이면_발행_대상이_아니다() {
        assertThat(FrontmatterReader.read("a.md", "---\npublish: \"true\"\n---\n")
                        .isPublishTarget())
                .isFalse();
    }

    @Test
    void YAML_오류나_같은_키가_둘이면_읽지_못한_노트다() {
        assertThat(FrontmatterReader.read("a.md", "---\ntitle: [\n---\n").unreadableReason())
                .isPresent();
        assertThat(FrontmatterReader.read("a.md", "---\npublish: false\npublish: true\n---\n")
                        .unreadableReason())
                .isPresent();
    }
}
