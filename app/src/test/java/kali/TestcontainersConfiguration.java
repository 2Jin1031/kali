package kali;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }

    /**
     * 마이그레이션 도구가 아직 없어서(ADR 0004) 테스트와 {@code bootTestRun}에서는 {@code db/migration}의 스크립트를 버전 순서로
     * 직접 적용한다. 컨테이너가 매번 새로 뜨므로 한 번씩만 적용된다.
     */
    @Bean
    DataSourceInitializer migrationInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(migrations()));
        return initializer;
    }

    static Resource[] migrations() {
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
            return Arrays.stream(resources)
                    .sorted(Comparator.comparingInt(TestcontainersConfiguration::version))
                    .toArray(Resource[]::new);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int version(Resource resource) {
        String name = Objects.requireNonNull(resource.getFilename());
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }
}
