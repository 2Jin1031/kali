package kali;

import org.springframework.boot.SpringApplication;

/** 로컬 실행용 진입점: {@code ./gradlew bootTestRun} 이 MySQL 컨테이너와 함께 앱을 띄운다. */
public class TestKaliApplication {

    public static void main(String[] args) {
        SpringApplication.from(KaliApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
