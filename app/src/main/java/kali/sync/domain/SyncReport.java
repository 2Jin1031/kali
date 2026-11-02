package kali.sync.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 동기화 한 번의 결과. 글은 노트 경로(발행 취소는 slug)로 적는다.
 *
 * @param published 처음 발행
 * @param republished {@code UNPUBLISHED}에서 다시 발행 (내용도 바뀌었으면 revised에도 있다)
 * @param revised 내용이 바뀌어 다시 씀 (수정 시각이 바뀜)
 * @param rehashed 해시 방식이 바뀌어 내용을 다시 씀 (수정 시각은 그대로)
 * @param moved 내용은 같고 노트 경로만 바뀜
 * @param unchanged 바뀐 것 없음
 * @param unpublished 이번 발행 대상에 없어 발행 취소한 글의 slug
 * @param skipped 건너뛴 노트와 이유
 */
public record SyncReport(
        List<String> published,
        List<String> republished,
        List<String> revised,
        List<String> rehashed,
        List<String> moved,
        List<String> unchanged,
        List<String> unpublished,
        List<SkippedNote> skipped) {

    public SyncReport {
        published = List.copyOf(published);
        republished = List.copyOf(republished);
        revised = List.copyOf(revised);
        rehashed = List.copyOf(rehashed);
        moved = List.copyOf(moved);
        unchanged = List.copyOf(unchanged);
        unpublished = List.copyOf(unpublished);
        skipped = List.copyOf(skipped);
    }

    /** 사람이 읽는 보고. 한 줄에 하나. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("발행 " + published.size() + ", 다시 발행 " + republished.size() + ", 수정 " + revised.size()
                + ", 해시 방식 변경으로 다시 씀 " + rehashed.size() + ", 경로만 바뀜 " + moved.size() + ", 그대로 "
                + unchanged.size() + ", 발행 취소 " + unpublished.size() + ", 건너뜀 " + skipped.size());
        published.forEach(path -> lines.add("발행: " + path));
        republished.forEach(path -> lines.add("다시 발행: " + path));
        revised.forEach(path -> lines.add("수정: " + path));
        rehashed.forEach(path -> lines.add("해시 방식 변경으로 다시 씀: " + path));
        moved.forEach(path -> lines.add("경로만 바뀜: " + path));
        unpublished.forEach(slug -> lines.add("발행 취소: " + slug));
        skipped.forEach(note -> lines.add("건너뜀: " + note.path() + " (" + note.reason() + ")"));
        return lines;
    }
}
