package kali.sync.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kali.post.domain.PostContent;

/**
 * 노트에서 읽어 글로 반영할 수 있는 상태가 된 것.
 *
 * @param content 이미지 경로를 바꾼 본문으로 만든 글 내용
 * @param originalBody 이미지 경로를 바꾸기 전, 줄바꿈만 LF로 맞춘 본문. 내용 해시에 넣는다 (스펙 5. 발행)
 * @param images 이미지 저장소 키(볼트 기준 경로)별 볼트 안 파일 경로. 지금은 둘이 같다. 본문에 처음 나온 순서
 */
public record PostDraft(UUID id, PostContent content, String originalBody, Map<String, String> images) {

    public PostDraft {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(originalBody, "originalBody");
        images = Collections.unmodifiableMap(new LinkedHashMap<>(images)); // 본문에 나온 순서로 올린다
    }

    public String sourcePath() {
        return content.sourcePath();
    }
}
