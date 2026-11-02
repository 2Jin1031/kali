package kali;

import kali.sync.infra.InMemoryImageStoreConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * 로컬 실행용 진입점: {@code ./gradlew bootTestRun} 이 MySQL 컨테이너와 함께 앱을 띄운다. 이미지는 메모리 저장소에 둔다. 동기화는
 * {@code --args='--kali.sync.enabled=true --kali.sync.vault=<볼트 경로>'}로 한 번 돌린다.
 */
public class TestKaliApplication {

    public static void main(String[] args) {
        SpringApplication.from(KaliApplication::main)
                .with(TestcontainersConfiguration.class, InMemoryImageStoreConfiguration.class)
                .run(args);
    }
}
