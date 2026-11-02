package kali.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PostValueTest {

    @ParameterizedTest
    @ValueSource(strings = {"winset", "spring-boot-4", "2026", "a-b-c"})
    void slug는_영문_소문자_숫자_하이픈을_받는다(String value) {
        assertThat(new Slug(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Winset", "win set", "win_set", "윈셋", "winset/2", "winset."})
    void slug_형식이_틀리면_거부한다(String value) {
        assertThatThrownBy(() -> new Slug(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void title이_있으면_그대로_제목으로_쓴다() {
        assertThat(PostTitle.resolve("윈셋이란 무엇인가", "notes/윈셋.md")).isEqualTo("윈셋이란 무엇인가");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    void title이_없거나_비어_있거나_공백만_있으면_확장자를_뺀_파일_이름을_제목으로_쓴다(String title) {
        assertThat(PostTitle.resolve(title, "concepts/윈셋.md")).isEqualTo("윈셋");
    }

    @Test
    void 파일_이름에_점이_여럿이면_마지막_확장자만_뺀다() {
        assertThat(PostTitle.resolve(null, "notes/spring.boot.4.md")).isEqualTo("spring.boot.4");
    }

    @Test
    void title의_앞뒤_공백은_지운다() {
        assertThat(PostTitle.resolve("  윈셋이란  ", "notes/윈셋.md")).isEqualTo("윈셋이란");
    }

    @Test
    void 직접_쓴_요약은_비어_있을_수_없다() {
        assertThatThrownBy(() -> Summary.manual(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 자동_요약은_비어_있어도_된다() {
        assertThat(Summary.auto("").source()).isEqualTo(Summary.Source.AUTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void 내용_해시는_비어_있을_수_없다(String value) {
        assertThatThrownBy(() -> new ContentHash(value, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 해시_방식_버전은_1_이상이다() {
        assertThatThrownBy(() -> new ContentHash("hash", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void id는_대문자가_섞여도_소문자_UUID와_같은_글이다() {
        assertThat(PostId.parse("0B6F2C1E-6A4D-4A8E-9C1F-2D3E4F5A6B7C"))
                .isEqualTo(PostId.parse("0b6f2c1e-6a4d-4a8e-9c1f-2d3e4f5a6b7c"))
                .isEqualTo(UUID.fromString("0b6f2c1e-6a4d-4a8e-9c1f-2d3e4f5a6b7c"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "1-2-3-4-5",
                "0b6f2c1e6a4d4a8e9c1f2d3e4f5a6b7c",
                "0b6f2c1e-6a4d-4a8e-9c1f-2d3e4f5a6b7",
                "g b6f2c1e-6a4d-4a8e-9c1f-2d3e4f5a6b7c",
                " 0b6f2c1e-6a4d-4a8e-9c1f-2d3e4f5a6b7c"
            })
    void id가_UUID_형식이_아니면_거부한다(String value) {
        assertThatThrownBy(() -> PostId.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
