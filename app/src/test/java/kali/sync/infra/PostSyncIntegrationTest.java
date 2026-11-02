package kali.sync.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kali.TestcontainersConfiguration;
import kali.post.domain.Post;
import kali.post.domain.PostRepository;
import kali.post.domain.Tag;
import kali.sync.application.PostSyncService;
import kali.sync.domain.SyncReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 볼트 폴더 → 동기화 → MySQL까지 실제 구현으로 한 번 돈다. 동기화 규칙은 PostSyncServiceTest가 본다. */
@SpringBootTest(properties = "kali.sync.enabled=true")
@Import({TestcontainersConfiguration.class, InMemoryImageStoreConfiguration.class})
class PostSyncIntegrationTest {

    static final UUID ID = UUID.fromString("c97535fc-f85c-47c7-8d47-67675b1a3bcf");

    @TempDir
    Path vault;

    @Autowired
    PostSyncService syncService;

    @Autowired
    PostRepository posts;

    @Autowired
    JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        jdbcClient.sql("DELETE FROM post_tag").update();
        jdbcClient.sql("DELETE FROM post_slug").update();
        jdbcClient.sql("DELETE FROM post").update();
    }

    @Test
    void 볼트의_노트를_발행하고_다시_동기화하면_그대로다() throws IOException {
        Files.createDirectories(vault.resolve("글"));
        Files.createDirectories(vault.resolve("image"));
        Files.writeString(vault.resolve("image/a.png"), "png");
        Files.writeString(
                vault.resolve("글/밋업.md"),
                "---\nid: " + ID + "\ntitle: \"[초록] 밋업\"\nslug: meetup\ndate: 2026-08-27\ntags:\n  - 초록\n"
                        + "publish: true\n---\n본문 ![[a.png]]\n");

        SyncReport first = syncService.sync(new FileSystemVault(vault));
        SyncReport second = syncService.sync(new FileSystemVault(vault));

        Post post = posts.findById(ID).orElseThrow();
        assertThat(first.published()).containsExactly("글/밋업.md");
        assertThat(second.unchanged()).containsExactly("글/밋업.md");
        assertThat(post.title()).isEqualTo("[초록] 밋업");
        assertThat(post.tags()).containsExactly(new Tag("초록"));
        assertThat(post.body()).isEqualTo("본문 ![a.png](https://images.test/image/a.png)\n");
    }
}
