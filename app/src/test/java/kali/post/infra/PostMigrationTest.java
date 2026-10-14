package kali.post.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import kali.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** db/migration/V1__create_post.sql */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        jdbcClient.sql("DELETE FROM post_slug").update();
        jdbcClient.sql("DELETE FROM post").update();
    }

    @Test
    void Flyway가_글_테이블_마이그레이션을_적용한다() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");

        var tables = jdbcClient
                .sql("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")
                .query(String.class)
                .list();
        assertThat(tables).contains("post", "post_slug");
    }

    @Test
    void 한_slug는_post_slug에_한_글만_가질_수_있다() {
        String first = 글_행을_넣는다("winset");
        String second = 글_행을_넣는다("winset-2");
        slug_기록을_넣는다("old", first);

        assertThatThrownBy(() -> slug_기록을_넣는다("old", second)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 글_행의_지금_slug도_두_글이_같을_수_없다() {
        글_행을_넣는다("winset");

        assertThatThrownBy(() -> 글_행을_넣는다("winset")).isInstanceOf(DuplicateKeyException.class);
    }

    private String 글_행을_넣는다(String slug) {
        String id = UUID.randomUUID().toString();
        jdbcClient.sql("""
                        INSERT INTO post (id, slug, title, body, summary, summary_source, status, display_date,
                                          first_published_at, content_updated_at, content_hash, content_hash_version,
                                          source_path)
                        VALUES (:id, :slug, '윈셋', '', '', 'AUTO', 'PUBLISHED', '2026-10-01',
                                '2026-10-01 00:00:00', '2026-10-01 00:00:00', 'h', 1, 'notes/winset.md')
                        """).param("id", id).param("slug", slug).update();
        return id;
    }

    private void slug_기록을_넣는다(String slug, String postId) {
        jdbcClient
                .sql("INSERT INTO post_slug (slug, post_id) VALUES (:slug, :postId)")
                .param("slug", slug)
                .param("postId", postId)
                .update();
    }
}
