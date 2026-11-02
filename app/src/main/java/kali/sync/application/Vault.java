package kali.sync.application;

import java.util.List;
import kali.sync.domain.Note;

/** 동기화가 읽는 옵시디언 볼트 (스펙 5. 발행). 읽기만 한다. */
public interface Vault {

    /** 볼트의 모든 노트({@code .md}). 숨김 폴더({@code .obsidian}, {@code .trash} 등)는 뺀다. */
    List<Note> notes();

    /** 노트가 아닌 파일까지 포함한 모든 파일의 볼트 기준 경로. 본문 이미지를 찾을 때 쓴다. */
    List<String> files();

    byte[] read(String path);
}
