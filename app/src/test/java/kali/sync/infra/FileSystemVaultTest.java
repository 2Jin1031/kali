package kali.sync.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kali.sync.domain.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemVaultTest {

    @TempDir
    Path root;

    void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void 숨김_폴더를_빼고_볼트_기준_경로로_읽는다() throws IOException {
        write("글/a.md", "---\npublish: true\n---\n본문");
        write("image/a.png", "png");
        write(".obsidian/app.json", "{}");
        write(".trash/old.md", "---\npublish: true\n---\n");

        FileSystemVault vault = new FileSystemVault(root);

        assertThat(vault.files()).containsExactly("image/a.png", "글/a.md");
        assertThat(vault.notes()).extracting(Note::path).containsExactly("글/a.md");
        assertThat(vault.read("image/a.png")).asString().isEqualTo("png");
    }

    @Test
    void 볼트_밖_파일은_읽지_않는다() throws IOException {
        Files.writeString(root.resolveSibling("outside.png"), "x");
        FileSystemVault vault = new FileSystemVault(root);

        assertThatThrownBy(() -> vault.read("../outside.png")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 없는_폴더는_볼트로_열지_않는다() {
        assertThatThrownBy(() -> new FileSystemVault(root.resolve("없음"))).isInstanceOf(IllegalArgumentException.class);
    }
}
