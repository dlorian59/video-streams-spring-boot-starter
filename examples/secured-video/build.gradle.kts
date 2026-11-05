plugins { java; application }
repositories {
    maven { url = uri("../../build/local-repository") }
    maven { url = uri("../../../video-streams/build/local-repository") }
    maven { url = uri("../../../file-streams-spring-boot-starter/build/local-repository") }
    maven { url = uri("../../../file-streams/build/local-repository") }
    mavenLocal(); mavenCentral()
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17); options.compilerArgs.add("-parameters") }
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation("io.github.59alex:video-streams-spring-boot-starter:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform(); maxHeapSize = "256m" }
application { mainClass.set("example.video.VideoApplication") }
