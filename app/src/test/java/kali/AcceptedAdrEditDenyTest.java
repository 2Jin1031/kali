package kali;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 수락된 ADR마다 {@code .claude/settings.json} deny에 Edit 차단 줄이 있어야 한다 (docs/adr/README.md).
 *
 * <p>가드(.github/scripts/guard.sh)는 {@code 사람-확인} 라벨이 있으면 통과하므로, 라벨과 상관없이 실패하도록 {@code ./gradlew check}에
 * 둔다.
 */
class AcceptedAdrEditDenyTest {

    private static final Pattern ACCEPTED = Pattern.compile("^상태:\\s*수락됨\\s*$", Pattern.MULTILINE);

    @Test
    void 수락된_ADR마다_settings_json_deny에_Edit_차단_줄이_있다() throws IOException {
        Path root = repositoryRoot();

        assertThat(acceptedAdrFiles(root)).isNotEmpty();
        assertThat(missingDenyRules(root))
                .as("수락된 ADR의 Edit 차단 줄이 .claude/settings.json deny에 없음. ADR을 수락하는 PR에서 같이 추가한다")
                .isEmpty();
    }

    @Test
    void 차단_줄이_하나_빠지면_그_줄을_찾아낸다(@TempDir Path copy) throws IOException {
        Path root = repositoryRoot();
        Files.createDirectories(copy.resolve(".claude"));
        Files.writeString(
                copy.resolve(".claude/settings.json"),
                Files.readString(root.resolve(".claude/settings.json"))
                        .replace("\"Edit(/docs/adr/0011-package-structure.md)\",", ""));
        Files.createDirectories(copy.resolve("docs/adr"));
        for (String name : acceptedAdrFiles(root)) {
            Files.copy(
                    root.resolve("docs/adr").resolve(name),
                    copy.resolve("docs/adr").resolve(name));
        }

        assertThat(missingDenyRules(copy)).contains("Edit(/docs/adr/0011-package-structure.md)");
    }

    @Test
    void 제안_상태_ADR은_차단_줄이_없어도_된다(@TempDir Path copy) throws IOException {
        Files.createDirectories(copy.resolve(".claude"));
        Files.writeString(copy.resolve(".claude/settings.json"), "{\"permissions\": {\"deny\": []}}");
        Files.createDirectories(copy.resolve("docs/adr"));
        Files.writeString(copy.resolve("docs/adr/0099-proposal.md"), "# 0099. 제안\n\n상태: 제안\n");
        Files.writeString(copy.resolve("docs/adr/README.md"), "| 0099 | 제안 | 수락됨 |\n");

        assertThat(missingDenyRules(copy)).isEmpty();
    }

    static List<String> missingDenyRules(Path root) throws IOException {
        List<String> deny = denyRules(root.resolve(".claude/settings.json"));
        return acceptedAdrFiles(root).stream()
                .map(name -> "Edit(/docs/adr/" + name + ")")
                .filter(rule -> !deny.contains(rule))
                .toList();
    }

    private static List<String> acceptedAdrFiles(Path root) throws IOException {
        List<String> accepted = new ArrayList<>();
        try (Stream<Path> files = Files.list(root.resolve("docs/adr"))) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("\\d{4}-.*\\.md"))
                    .sorted()
                    .toList()) {
                if (ACCEPTED.matcher(Files.readString(file)).find()) {
                    accepted.add(file.getFileName().toString());
                }
            }
        }
        return accepted;
    }

    private static List<String> denyRules(Path settings) throws IOException {
        JsonNode deny = JsonMapper.builder()
                .build()
                .readTree(Files.readString(settings))
                .path("permissions")
                .path("deny");
        List<String> rules = new ArrayList<>();
        deny.forEach(rule -> rules.add(rule.asString()));
        return rules;
    }

    // 테스트는 app/에서 돈다. settings.gradle.kts가 있는 곳이 저장소 루트다
    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
        }
        throw new IllegalStateException("settings.gradle.kts가 있는 저장소 루트를 찾지 못함");
    }
}
