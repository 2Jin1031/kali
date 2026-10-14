-- 글 (docs/spec/domain.md 3~6절). 시각 칸은 모두 UTC로 저장한다.
-- 태그, 대표 여부·순서는 스펙 미정이라 아직 칸을 두지 않는다.
CREATE TABLE post (
    id                   CHAR(36)      NOT NULL COMMENT 'frontmatter id (UUID)',
    slug                 VARCHAR(200)  NOT NULL COMMENT '지금 slug',
    title                VARCHAR(255)  NOT NULL,
    body                 MEDIUMTEXT    NOT NULL,
    summary              TEXT          NOT NULL,
    summary_source       VARCHAR(10)   NOT NULL COMMENT 'MANUAL, AUTO',
    status               VARCHAR(20)   NOT NULL COMMENT 'PUBLISHED, UNPUBLISHED',
    display_date         DATE          NOT NULL COMMENT '화면 날짜',
    first_published_at   DATETIME(6)   NOT NULL COMMENT '처음 발행 시각 (UTC)',
    content_updated_at   DATETIME(6)   NOT NULL COMMENT '내용을 마지막으로 쓴 시각 (UTC)',
    content_hash         VARCHAR(128)  NOT NULL,
    content_hash_version INT           NOT NULL COMMENT '해시를 계산한 방식',
    source_path          VARCHAR(1024) NOT NULL COMMENT '식별자가 아님',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_slug (slug),
    KEY idx_post_status_display_date (status, display_date DESC, first_published_at DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 모든 글이 지금 쓰거나 예전에 쓴 slug. 기본 키로 모든 글에서 slug가 유일하다
CREATE TABLE post_slug (
    slug    VARCHAR(200) NOT NULL,
    post_id CHAR(36)     NOT NULL,
    PRIMARY KEY (slug),
    KEY idx_post_slug_post_id (post_id),
    CONSTRAINT fk_post_slug_post FOREIGN KEY (post_id) REFERENCES post (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
