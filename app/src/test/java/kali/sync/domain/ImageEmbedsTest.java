package kali.sync.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageEmbedsTest {

    static String rewrite(String body) {
        return ImageEmbeds.rewrite(body, target -> "URL(" + target + ")");
    }

    @Test
    void 옵시디언_이미지_임베드를_마크다운_이미지로_바꾼다() {
        assertThat(rewrite("![[a.png]] ![[폴더/b.JPG|200]]")).isEqualTo("![a.png](URL(a.png)) ![b.JPG](URL(폴더/b.JPG))");
    }

    @Test
    void 볼트_안을_가리키는_마크다운_이미지도_바꾸고_인코딩을_푼다() {
        assertThat(rewrite("![설명](image/Pasted%20image%201.png) ![](<image/a b.png>)"))
                .isEqualTo("![설명](URL(image/Pasted image 1.png)) ![](URL(image/a b.png))");
    }

    @Test
    void 바깥_주소_이미지와_이미지가_아닌_임베드는_그대로_둔다() {
        String body = "![](https://example.com/a.png) ![[다른 노트]] ![[문서.pdf]] [[a.png]]";

        assertThat(rewrite(body)).isEqualTo(body);
    }

    @Test
    void 코드_블록과_인라인_코드_안은_바꾸지_않는다() {
        String body = "```\n![[a.png]]\n```\n`![[b.png]]`\n~~~md\n![[c.png]]\n~~~\n![[d.png]]";

        assertThat(rewrite(body))
                .isEqualTo("```\n![[a.png]]\n```\n`![[b.png]]`\n~~~md\n![[c.png]]\n~~~\n![d.png](URL(d.png))");
    }

    @Test
    void 닫히지_않은_코드_블록은_끝까지_코드다() {
        String body = "```\n![[a.png]]";

        assertThat(rewrite(body)).isEqualTo(body);
    }
}
