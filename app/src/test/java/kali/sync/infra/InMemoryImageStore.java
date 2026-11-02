package kali.sync.infra;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import kali.sync.application.ImageStore;

/** 테스트와 로컬 실행용 이미지 저장소. 올린 이미지를 메모리에만 둔다. 실제 저장소(S3 등)는 인프라 단계에서 만든다. */
public class InMemoryImageStore implements ImageStore {

    public final Map<String, byte[]> stored = new LinkedHashMap<>();
    public int puts;

    @Override
    public String urlOf(String key) {
        return "https://images.test/"
                + URLEncoder.encode(key, StandardCharsets.UTF_8)
                        .replace("+", "%20")
                        .replace("%2F", "/");
    }

    @Override
    public void put(String key, byte[] content) {
        stored.put(key, content.clone());
        puts++;
    }
}
