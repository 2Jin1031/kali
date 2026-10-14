package kali;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigrationTest {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void 마이그레이션을_적용하면_글_테이블이_생긴다() {
        var tables = jdbcClient
                .sql("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")
                .query(String.class)
                .list();

        assertThat(tables).contains("post", "post_slug");
    }
}
