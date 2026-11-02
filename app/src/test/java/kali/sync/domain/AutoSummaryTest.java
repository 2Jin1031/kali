package kali.sync.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutoSummaryTest {

    @Test
    void 마크다운_기호와_이미지와_코드를_빼고_본문_앞부분으로_만든다() {
        String body = "## 0. 서문\n\n**초록**이란 [[커뮤니티|모임]]이다 ![[a.png]]\n```java\ncode\n```\n- [홈](https://x)";

        assertThat(AutoSummary.from(body)).isEqualTo("0. 서문 초록이란 모임이다 홈");
    }

    @Test
    void 길면_150자에서_자르고_말줄임표를_붙인다() {
        String summary = AutoSummary.from("가".repeat(200));

        assertThat(summary).isEqualTo("가".repeat(150) + "…");
    }
}
