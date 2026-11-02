package kali.sync.domain;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 본문 이미지의 이름이나 경로로 볼트 안 파일을 찾는다. 먼저 노트 폴더 기준, 다음 볼트 기준으로 정확히 그 경로인 파일을 찾는다. 없으면
 * 위키링크와 같이 대소문자를 구분하지 않고, 경로가 있으면 폴더 단위로 그 경로로 끝나는 파일을 찾는다 (스펙 7. 링크의 위키링크 해석).
 */
public final class ImageResolver {

    private final List<String> files;

    /** @param files 볼트 기준 상대 경로 목록 */
    public ImageResolver(Collection<String> files) {
        this.files = List.copyOf(files);
    }

    /**
     * @param notePath 이미지를 쓴 노트의 볼트 기준 경로
     * @return 찾은 파일의 볼트 기준 경로
     * @throws IllegalArgumentException 맞는 파일이 없거나 둘 이상일 때
     */
    public String resolve(String target, String notePath) {
        String raw = target.strip().replace('\\', '/');
        Path noteDir = Path.of(notePath).getParent();
        Optional<String> exact = exact(noteDir == null ? Path.of(raw) : noteDir.resolve(raw))
                .or(() -> exact(Path.of(raw.startsWith("/") ? raw.substring(1) : raw)));
        if (exact.isPresent()) {
            return exact.get();
        }
        String wanted = normalize(target);
        List<String> matches = files.stream()
                .filter(path -> {
                    String candidate = path.toLowerCase(Locale.ROOT);
                    return candidate.equals(wanted) || candidate.endsWith("/" + wanted);
                })
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("볼트에 없는 이미지: " + target);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("이미지 이름이 파일 둘 이상에 맞음: " + target + " → " + matches);
        }
        return matches.getFirst();
    }

    private Optional<String> exact(Path path) {
        String normalized = path.normalize().toString().replace('\\', '/');
        if (normalized.startsWith("..")) {
            return Optional.empty();
        }
        return files.stream().filter(normalized::equals).findFirst();
    }

    // 정확히 맞는 파일이 없을 때: ./a.png, ../image/a.png, /a.png는 앞을 떼고 찾는다
    private static String normalize(String target) {
        String path = target.strip().replace('\\', '/');
        while (path.startsWith("./") || path.startsWith("../") || path.startsWith("/")) {
            path = path.substring(path.indexOf('/') + 1);
        }
        return path.toLowerCase(Locale.ROOT);
    }
}
