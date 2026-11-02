package kali.sync.domain;

import java.util.Optional;
import java.util.UUID;

/** 노트를 글로 읽을 수 없어 건너뛸 때. {@code id}를 읽은 뒤라면 그 글은 DB에서 그대로 둔다 (스펙 5. 발행의 문제 있는 노트). */
public class NoteSkippedException extends RuntimeException {

    private final transient UUID id;

    public NoteSkippedException(String reason, UUID id) {
        super(reason);
        this.id = id;
    }

    public Optional<UUID> id() {
        return Optional.ofNullable(id);
    }
}
