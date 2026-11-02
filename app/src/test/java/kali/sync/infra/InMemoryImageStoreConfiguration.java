package kali.sync.infra;

import kali.sync.application.ImageStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** 실제 이미지 저장소가 생기기 전까지 동기화를 띄울 때 쓰는 메모리 저장소. */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryImageStoreConfiguration {

    @Bean
    ImageStore imageStore() {
        return new InMemoryImageStore();
    }
}
