package kali.sync.application;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kali.sync.domain.Note;

/** 테스트용 볼트. 노트는 frontmatter 맵으로 직접 넣는다. */
class InMemoryVault implements Vault {

    final Map<String, Note> notes = new LinkedHashMap<>();
    final Map<String, byte[]> attachments = new LinkedHashMap<>();

    InMemoryVault note(String path, Map<String, Object> frontmatter, String body) {
        notes.put(path, Note.of(path, frontmatter, body));
        return this;
    }

    InMemoryVault image(String path, String content) {
        attachments.put(path, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    @Override
    public List<Note> notes() {
        return List.copyOf(notes.values());
    }

    @Override
    public List<String> files() {
        List<String> files = new ArrayList<>(notes.keySet());
        files.addAll(attachments.keySet());
        return files;
    }

    @Override
    public byte[] read(String path) {
        byte[] content = attachments.get(path);
        if (content == null) {
            throw new IllegalArgumentException("없는 파일: " + path);
        }
        return content;
    }
}
