package kali.sync.infra;

import java.util.LinkedHashMap;
import java.util.Map;
import kali.sync.domain.Note;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * 노트 글자를 frontmatter와 본문으로 나눈다. frontmatter는 첫 줄의 {@code ---}부터 다음 {@code ---} 줄까지다 (옵시디언 속성).
 * 날짜처럼 보이는 값은 날짜로 바꾸지 않고 글자 그대로 둔다 (스펙 6. 글 속성의 발행일은 원래 글자로 형식을 검사한다).
 */
final class FrontmatterReader {

    private FrontmatterReader() {}

    static Note read(String path, String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return Note.of(path, Map.of(), text);
        }
        int close = closingLine(normalized);
        if (close < 0) {
            return Note.of(path, Map.of(), text);
        }
        String yaml = normalized.substring(4, close);
        int bodyStart = normalized.indexOf('\n', close);
        String body = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1);
        try {
            Object loaded = yaml().load(yaml);
            if (loaded == null) {
                return Note.of(path, Map.of(), body);
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                return Note.unreadable(path, normalized, "frontmatter가 키: 값 모양이 아님");
            }
            Map<String, Object> frontmatter = new LinkedHashMap<>();
            map.forEach((key, value) -> frontmatter.put(String.valueOf(key), value));
            return Note.of(path, frontmatter, body);
        } catch (YAMLException e) {
            return Note.unreadable(path, normalized, firstLine(e.getMessage()));
        }
    }

    // 둘째 줄부터 처음 나오는 "---" 줄의 시작 위치
    private static int closingLine(String text) {
        int lineStart = 4;
        while (lineStart <= text.length()) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline;
            if (text.substring(lineStart, lineEnd).stripTrailing().equals("---")) {
                return lineStart;
            }
            if (newline < 0) {
                return -1;
            }
            lineStart = newline + 1;
        }
        return -1;
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(
                new SafeConstructor(options),
                new Representer(new DumperOptions()),
                new DumperOptions(),
                options,
                new StringTimestampResolver());
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "YAML 오류";
        }
        return message.lines().findFirst().orElse(message).strip();
    }

    private static final class StringTimestampResolver extends Resolver {
        @Override
        public Tag resolve(NodeId kind, String value, boolean implicit) {
            Tag tag = super.resolve(kind, value, implicit);
            return Tag.TIMESTAMP.equals(tag) ? Tag.STR : tag;
        }
    }
}
