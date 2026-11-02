package kali.sync.application;

/**
 * 본문 첨부 이미지를 옮겨 두는 저장소 (스펙 5. 발행). 종류는 인프라 단계에서 정한다. 종류나 주소를 바꾸면
 * {@link kali.sync.domain.ContentHasher#VERSION}을 올린다.
 */
public interface ImageStore {

    /** 키로 본문에 넣을 주소를 만든다. 올리기 전에도 같은 값을 돌려준다 (본문 길이 제한을 올리기 전에 검사한다). */
    String urlOf(String key);

    /** 같은 키가 있으면 덮어쓴다. */
    void put(String key, byte[] content);
}
