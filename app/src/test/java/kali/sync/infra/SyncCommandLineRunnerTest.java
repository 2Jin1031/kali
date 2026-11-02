package kali.sync.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kali.post.domain.Post;
import kali.post.domain.PostRepository;
import kali.post.domain.Slug;
import kali.sync.application.NoPublishTargetException;
import kali.sync.application.PostSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class SyncCommandLineRunnerTest {

    @TempDir
    Path vault;

    final List<Post> saved = new ArrayList<>();
    final PostRepository posts = new PostRepository() {
        @Override
        public void save(Post post) {
            saved.add(post);
        }

        @Override
        public Optional<Post> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<Post> findAll() {
            return List.of();
        }

        @Override
        public Optional<Post> findBySlug(Slug slug) {
            return Optional.empty();
        }
    };

    SyncCommandLineRunner runner() {
        return new SyncCommandLineRunner(new PostSyncService(posts, new InMemoryImageStore()), vault);
    }

    @Test
    void 지정한_볼트_폴더를_동기화한다() throws IOException {
        Files.writeString(
                vault.resolve("a.md"),
                "---\nid: c97535fc-f85c-47c7-8d47-67675b1a3bcf\nslug: winset\npublish: true\n---\n본문");

        runner().run(new DefaultApplicationArguments());

        assertThat(saved).extracting(post -> post.slug().value()).containsExactly("winset");
    }

    @Test
    void 발행_대상이_없으면_실패를_그대로_올린다() {
        assertThatThrownBy(() -> runner().run(new DefaultApplicationArguments()))
                .isInstanceOf(NoPublishTargetException.class);
    }
}
