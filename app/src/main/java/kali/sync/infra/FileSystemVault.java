package kali.sync.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import kali.sync.application.Vault;
import kali.sync.domain.Note;

/** 로컬 폴더에 있는 옵시디언 볼트. 읽기만 한다. 이름이 {@code .}으로 시작하는 폴더와 파일(.obsidian, .trash, .git 등)은 뺀다. */
public class FileSystemVault implements Vault {

    private final Path root;

    public FileSystemVault(Path root) {
        this.root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root)) {
            throw new IllegalArgumentException("볼트 폴더가 없다: " + this.root);
        }
    }

    @Override
    public List<Note> notes() {
        return files().stream()
                .filter(path -> path.endsWith(".md"))
                .map(path -> FrontmatterReader.read(path, new String(read(path), StandardCharsets.UTF_8)))
                .toList();
    }

    @Override
    public List<String> files() {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .filter(FileSystemVault::visible)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] read(String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("볼트 밖 경로: " + path);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean visible(Path relative) {
        for (Path part : relative) {
            if (part.toString().startsWith(".")) {
                return false;
            }
        }
        return true;
    }
}
