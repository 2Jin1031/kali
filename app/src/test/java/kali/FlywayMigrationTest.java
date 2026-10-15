package kali;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** docs/adr/0012-persistence-and-migration.md: 스키마는 Flyway 마이그레이션으로만 바꾸고, 앱이 뜰 때 적용한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void 앱이_뜰_때_MySQL에_마이그레이션을_적용하고_기록_테이블을_만든다() {
        Integer historyTables = jdbcClient.sql("""
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'
                        """).query(Integer.class).single();

        assertThat(historyTables).isEqualTo(1);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void 마이그레이션_파일은_db_migration에서만_읽는다() {
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Location::getDescriptor)
                .containsExactly("classpath:db/migration");
    }
}
