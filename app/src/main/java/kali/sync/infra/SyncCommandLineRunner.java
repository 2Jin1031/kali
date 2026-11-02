package kali.sync.infra;

import java.nio.file.Path;
import kali.sync.application.PostSyncService;
import kali.sync.domain.SyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 앱이 뜰 때 {@code kali.sync.vault} 경로의 볼트를 한 번 동기화하고 보고를 로그로 남기는 임시 입구. 동기화를 실행하는 입구의 모양(관리용
 * API, 명령줄, 예약 실행)은 정하지 않았다.
 *
 * <p>예: {@code ./gradlew bootTestRun --args='--kali.sync.enabled=true --kali.sync.vault=/path/to/ai'}
 */
@Component
@ConditionalOnProperty(name = "kali.sync.vault")
class SyncCommandLineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncCommandLineRunner.class);

    private final PostSyncService syncService;
    private final Path vault;

    SyncCommandLineRunner(PostSyncService syncService, @Value("${kali.sync.vault}") Path vault) {
        this.syncService = syncService;
        this.vault = vault;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("볼트 동기화 시작: {}", vault);
        SyncReport report = syncService.sync(new FileSystemVault(vault));
        report.lines().forEach(line -> log.info("{}", line));
    }
}
