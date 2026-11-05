plugins { `java-library`; `maven-publish` }
group = "io.github.59alex"
version = "0.1.0"
repositories {
    maven { url = uri("../video-streams/build/local-repository") }
    maven { url = uri("../file-streams/build/local-repository") }
    maven { url = uri("../file-streams-spring-boot-starter/build/local-repository") }
    mavenLocal()
    mavenCentral()
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(
        providers.gradleProperty("testJavaVersion").getOrElse(JavaVersion.current().majorVersion).toInt()))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar(); withJavadocJar()
}
dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    api("io.github.59alex:file-streams-spring-boot-starter:0.1.0")
    api("io.github.59alex:video-streams:0.1.0")
    api("io.github.59alex:video-streams-webflux:0.1.0")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.1.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17); options.encoding = "UTF-8" }
tasks.withType<Test>().configureEach { useJUnitPlatform(); maxHeapSize = "256m" }
tasks.withType<Jar>().configureEach { from("LICENSE") { into("META-INF") } }
publishing {
    repositories { maven { name = "localArtifacts"; url = uri(layout.buildDirectory.dir("local-repository")) } }
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set("Video Streams Spring Boot Starter")
                description.set("Opt-in Spring Boot integration for HLS VOD preparation over continuous ObjectStreams storage")
                url.set("https://github.com/dlorian59/video-streams-spring-boot-starter")
                licenses { license { name.set("MIT License"); url.set("https://opensource.org/licenses/MIT") } }
                developers { developer { id.set("dlorian59"); name.set("Aleksandr Kashevarov") } }
                scm { url.set("https://github.com/dlorian59/video-streams-spring-boot-starter") }
            }
        }
    }
}
