plugins {
    // JDK 25가 없는 환경(새 머신, 밤 작업 환경)에서 Gradle이 자동으로 내려받게 한다
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kali"

include("app")
