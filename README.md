# video-streams-spring-boot-starter

<details name="readme-language">
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-en-dark.svg"><img src="assets/lang-en-light.svg" width="42" height="26" alt="EN"></picture></summary>

**Version:** [0.1.0](docs/0.1.0/en.md) · [Versions](docs/index.md) · Java 17+ · Spring Boot 4.1.2

Prepare an uploaded video for playback without downloading the complete video into a browser Blob. This starter connects FFmpeg-based HLS preparation to the same ObjectStreams storage used for ordinary file uploads. Your application supplies access rules; the browser plays the prepared playlist and requests its segments as needed.

## Add to an existing application

This integration targets **Spring WebFlux**. Keep your existing Boot 4.1.2 BOM/plugin and authentication. The dependency includes file-streams-spring-boot-starter and the video core/WebFlux modules; do not add a second storage client. Artifacts are currently source/local-repository only, not published to Maven Central. [Local development](CONTRIBUTING.md).

### 1. Dependency

Maven:
```xml
<dependency>
  <groupId>io.github.59alex</groupId>
  <artifactId>video-streams-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```
Gradle Kotlin DSL:
```kotlin
dependencies {
    implementation("io.github.59alex:video-streams-spring-boot-starter:0.1.0")
}
```

### 2. Configuration

FFmpeg and ffprobe must be available as external executables. Give preparation a private writable work directory with sufficient space. Keep storage credentials in your application's existing AWS credentials provider; no access key belongs in YAML.

```yaml
file-streams:
  enabled: true
  http:
    enabled: false
  limits:
    profile: MEDIA
  s3:
    bucket: ${VIDEO_BUCKET}
    region: ${AWS_REGION}
    endpoint: ${S3_ENDPOINT}
video-streams:
  enabled: true
  work-directory: ${VIDEO_WORK_DIRECTORY}
```

If file-streams is already configured, add only video-streams.enabled and work-directory. The MEDIA profile permits 512 MiB per source, matching the video default. HTTP exposure stays disabled: **the starter adds beans, never a public media route**.

### 3. Service and authorized controller

Put the following files under your existing application's scan package. Rename package example.video to your project package.

```text
src/main/java/example/video/
  VideoService.java
  VideoController.java
  VideoErrors.java
src/main/resources/application.yml
```

The service prepares an already committed source and returns a playback URL after readiness:

```java
package example.video;

import io.github.alex59.videostreams.VideoStreams;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

@Service
public class VideoService {
    public record PreparedVideo(String status, UUID generation, String playlistUrl,
                                double durationSeconds, int segmentCount) { }
    private final VideoStreams videos;

    public VideoService(VideoStreams videos) { this.videos = videos; }

    public Mono<PreparedVideo> prepare(String authorizedSourceKey) {
        // EN: Prepare a committed object; readiness is returned only after the core publishes the playlist last.
        // RU: Готовим уже сохранённый объект: ready возвращается только после публикации плейлиста последним.
        return videos.prepare(authorizedSourceKey).map(asset -> new PreparedVideo("ready", asset.generation(),
                "/api/videos/" + UriUtils.encodePathSegment(authorizedSourceKey, StandardCharsets.UTF_8)
                        + "/assets/" + asset.generation() + "/playlist.m3u8",
                asset.durationSeconds(), asset.segmentCount()));
    }
}
```

The complete controller authorizes before storage or preparation and checks each playlist/init/segment request:

```java
package example.video;

import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import io.github.alex59.videostreams.webflux.VideoAssetHandler;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class VideoController {
    public record PrepareRequest(String sourceKey) { }
    private final ObjectStreamHandler files;
    private final VideoAssetHandler assets;
    private final VideoService videos;

    public VideoController(ObjectStreamHandler files, VideoAssetHandler assets, VideoService videos) {
        this.files = files; this.assets = assets; this.videos = videos;
    }

    @PutMapping("/api/video-sources/{id}")
    public Mono<Void> upload(@PathVariable("id") String id, Principal account, ServerWebExchange exchange) {
        // EN: Use a new account-scoped ID for each source; preserve it for the prepare step. Do not overwrite a preparing source.
        // RU: Для каждого исходника используйте новый ID с областью аккаунта и сохраните для prepare. Не перезаписывайте готовящийся исходник.
        return files.handle(exchange, authorize(id, account));
    }

    @PostMapping("/api/videos/{id}/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<VideoService.PreparedVideo> prepare(@PathVariable("id") String id,
            @RequestBody PrepareRequest request, Principal account) {
        String sourceKey = authorize(id, account);
        if (!sourceKey.equals(request.sourceKey()))
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source does not match route"));
        return videos.prepare(sourceKey);
    }

    @GetMapping("/api/videos/{id}/assets/{generation}/{name}")
    public Mono<Void> asset(@PathVariable("id") String id, @PathVariable("generation") UUID generation,
            @PathVariable("name") String name, Principal account, ServerWebExchange exchange) {
        // EN: Recheck access on every playlist/init/segment request; knowing the generation is not authorization.
        // RU: Проверяем доступ к каждому плейлисту/init/сегменту: знание поколения не даёт права доступа.
        return assets.handle(exchange, authorize(id, account), generation, name);
    }

    private static String authorize(String id, Principal account) {
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!id.matches("[A-Za-z0-9_-]{1,64}:[A-Za-z0-9_-]{1,80}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected account:source identifier");
        // EN: Replace this sample namespace rule with your application's persisted ACL before calling any storage or process API.
        // RU: Замените правило пространства имён проверкой ACL приложения до любого вызова хранилища или процесса.
        if (!id.startsWith(account.getName() + ":")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return id;
    }
}
```

In this example, the authenticated account demo uploads a new source ID such as demo:quarterly-review. PUT /api/video-sources/demo%3Aquarterly-review uses the ordinary raw streaming upload and returns its commit. POST /api/videos/demo%3Aquarterly-review/prepare with {"sourceKey":"demo:quarterly-review"} returns HTTP 201 with status ready, generation, playlistUrl, durationSeconds and segmentCount. Preparation completes before the response; this is not a durable background job or a 202 polling API.

Replace the example namespace rule with your application's ACL where necessary. Use immutable source IDs or versioned metadata to prevent overwriting a source while it is being prepared. Add [VideoErrors](examples/secured-video/src/main/java/example/video/VideoErrors.java) for public error categories without leaking process diagnostics. The [complete secured consumer](examples/secured-video) adds actual session authentication and CSRF handling; copy only the application parts you need.

## Capabilities

1. **Prepare a stored video:** `videos.prepare(authorizedSourceKey)` → one immutable HLS VOD generation. No whole-heap source aggregation. [Full service](examples/secured-video/src/main/java/example/video/VideoService.java), [walkthrough](docs/0.1.0/en.md#preparation).
2. **Reuse file storage:** `file-streams.limits.profile: MEDIA` → the same ObjectStreams and S3 client serve source uploads and prepared artifacts. [Configuration](examples/secured-video/src/main/resources/application.yml), [storage ownership](docs/0.1.0/en.md#storage-and-lifecycle).
3. **Serve authorized segments:** `assets.handle(exchange, sourceKey, generation, name)` → existing streaming/range delivery after your access check. [Controller](examples/secured-video/src/main/java/example/video/VideoController.java), [playback](docs/0.1.0/en.md#playback).
4. **Bound preparation:** `video-streams.max-concurrent: 1` → limit active preparations without implementing an executor. Defaults also bound bytes, segment count and deadline. [Settings](docs/0.1.0/reference.en.md).
5. **Connect React upload and playback:** use [video-streams-react](https://github.com/dlorian59/video-streams-react) with the same source ID and prepare route. The file upload commit is separate from video readiness. [Full flow](docs/0.1.0/en.md#browser-flow).

V1 produces one H.264/AAC fMP4 HLS rendition, not an adaptive bitrate ladder or DRM system. FFmpeg is not bundled. Cancellation stops owned processing but is not storage rollback; a lost response during final publication can have an unknown outcome. [Limits and troubleshooting](docs/0.1.0/en.md#failures).

[Full guide](docs/0.1.0/en.md) · [API/defaults](docs/0.1.0/reference.en.md) · [Development](CONTRIBUTING.md) · [Verification](docs/VERIFICATION.md)

</details>

<details name="readme-language" open>
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-ru-dark.svg"><img src="assets/lang-ru-light.svg" width="42" height="26" alt="RU"></picture></summary>

**Версия:** [0.1.0](docs/0.1.0/ru.md) · [Версии](docs/index.md) · Java 17+ · Spring Boot 4.1.2

Подготовка загруженного видео к воспроизведению без скачивания целого файла в Blob браузера. Стартер соединяет HLS-подготовку через FFmpeg с тем же ObjectStreams, который обслуживает обычную загрузку файлов. Приложение задаёт правила доступа, браузер воспроизводит плейлист и подгружает нужные сегменты.

## Подключение к существующему приложению

Интеграция рассчитана на **Spring WebFlux**. Сохраните Boot 4.1.2 BOM/plugin и существующую аутентификацию. Зависимость включает file-streams-spring-boot-starter и модули video core/WebFlux: второй клиент хранилища не нужен. Артефакты пока доступны из исходников/локального репозитория, не из Maven Central. [Локальная разработка](CONTRIBUTING.md).

### 1. Зависимость

Maven:
```xml
<dependency>
  <groupId>io.github.59alex</groupId>
  <artifactId>video-streams-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```
Gradle Kotlin DSL:
```kotlin
dependencies {
    implementation("io.github.59alex:video-streams-spring-boot-starter:0.1.0")
}
```

### 2. Конфигурация

FFmpeg и ffprobe должны быть доступны как внешние исполняемые файлы. Выделите подготовке закрытый каталог с правом записи и достаточным местом. Учётные данные хранилища остаются в существующем AWS credentials provider; access key не нужно помещать в YAML.

```yaml
file-streams:
  enabled: true
  http:
    enabled: false
  limits:
    profile: MEDIA
  s3:
    bucket: ${VIDEO_BUCKET}
    region: ${AWS_REGION}
    endpoint: ${S3_ENDPOINT}
video-streams:
  enabled: true
  work-directory: ${VIDEO_WORK_DIRECTORY}
```

Если file-streams уже настроен, добавьте только video-streams.enabled и work-directory. Профиль MEDIA ограничивает исходник 512 МиБ, как и video-default. HTTP остаётся выключенным: **стартер добавляет beans, но не публичный маршрут к медиа**.

### 3. Сервис и контроллер с проверкой доступа

Поместите файлы под существующим пакетом сканирования приложения. Замените package example.video на свой пакет.

```text
src/main/java/example/video/
  VideoService.java
  VideoController.java
  VideoErrors.java
src/main/resources/application.yml
```

Сервис готовит уже сохранённый исходник и возвращает ссылку после готовности:

```java
package example.video;

import io.github.alex59.videostreams.VideoStreams;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

@Service
public class VideoService {
    public record PreparedVideo(String status, UUID generation, String playlistUrl,
                                double durationSeconds, int segmentCount) { }
    private final VideoStreams videos;

    public VideoService(VideoStreams videos) { this.videos = videos; }

    public Mono<PreparedVideo> prepare(String authorizedSourceKey) {
        // EN: Prepare a committed object; readiness is returned only after the core publishes the playlist last.
        // RU: Готовим уже сохранённый объект: ready возвращается только после публикации плейлиста последним.
        return videos.prepare(authorizedSourceKey).map(asset -> new PreparedVideo("ready", asset.generation(),
                "/api/videos/" + UriUtils.encodePathSegment(authorizedSourceKey, StandardCharsets.UTF_8)
                        + "/assets/" + asset.generation() + "/playlist.m3u8",
                asset.durationSeconds(), asset.segmentCount()));
    }
}
```

Полный контроллер проверяет права до хранилища/подготовки и для каждого запроса плейлиста/init/сегмента:

```java
package example.video;

import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import io.github.alex59.videostreams.webflux.VideoAssetHandler;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class VideoController {
    public record PrepareRequest(String sourceKey) { }
    private final ObjectStreamHandler files;
    private final VideoAssetHandler assets;
    private final VideoService videos;

    public VideoController(ObjectStreamHandler files, VideoAssetHandler assets, VideoService videos) {
        this.files = files; this.assets = assets; this.videos = videos;
    }

    @PutMapping("/api/video-sources/{id}")
    public Mono<Void> upload(@PathVariable("id") String id, Principal account, ServerWebExchange exchange) {
        // EN: Use a new account-scoped ID for each source; preserve it for the prepare step. Do not overwrite a preparing source.
        // RU: Для каждого исходника используйте новый ID с областью аккаунта и сохраните для prepare. Не перезаписывайте готовящийся исходник.
        return files.handle(exchange, authorize(id, account));
    }

    @PostMapping("/api/videos/{id}/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<VideoService.PreparedVideo> prepare(@PathVariable("id") String id,
            @RequestBody PrepareRequest request, Principal account) {
        String sourceKey = authorize(id, account);
        if (!sourceKey.equals(request.sourceKey()))
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source does not match route"));
        return videos.prepare(sourceKey);
    }

    @GetMapping("/api/videos/{id}/assets/{generation}/{name}")
    public Mono<Void> asset(@PathVariable("id") String id, @PathVariable("generation") UUID generation,
            @PathVariable("name") String name, Principal account, ServerWebExchange exchange) {
        // EN: Recheck access on every playlist/init/segment request; knowing the generation is not authorization.
        // RU: Проверяем доступ к каждому плейлисту/init/сегменту: знание поколения не даёт права доступа.
        return assets.handle(exchange, authorize(id, account), generation, name);
    }

    private static String authorize(String id, Principal account) {
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!id.matches("[A-Za-z0-9_-]{1,64}:[A-Za-z0-9_-]{1,80}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected account:source identifier");
        // EN: Replace this sample namespace rule with your application's persisted ACL before calling any storage or process API.
        // RU: Замените правило пространства имён проверкой ACL приложения до любого вызова хранилища или процесса.
        if (!id.startsWith(account.getName() + ":")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return id;
    }
}
```

В примере аутентифицированный аккаунт demo загружает новый исходник с ID demo:quarterly-review. PUT /api/video-sources/demo%3Aquarterly-review использует обычный непрерывный поток и возвращает commit. POST /api/videos/demo%3Aquarterly-review/prepare с {"sourceKey":"demo:quarterly-review"} возвращает HTTP 201: status ready, generation, playlistUrl, durationSeconds и segmentCount. Подготовка заканчивается до ответа: это не долговечная фоновая задача и не 202 с опросом статуса.

При необходимости замените правило пространства имён на ACL приложения. Неизменяемые ID исходников или версионированные метаданные должны предотвращать перезапись во время подготовки. Добавьте [VideoErrors](examples/secured-video/src/main/java/example/video/VideoErrors.java) для публичных категорий ошибок без диагностики процесса. [Полный защищённый потребитель](examples/secured-video) включает реальную сессионную аутентификацию и CSRF; копируйте только нужные части приложения.

## Возможности

1. **Подготовить сохранённое видео:** `videos.prepare(authorizedSourceKey)` → неизменяемое поколение HLS VOD. Исходник не собирается целиком в heap. [Полный сервис](examples/secured-video/src/main/java/example/video/VideoService.java), [разбор](docs/0.1.0/ru.md#preparation).
2. **Переиспользовать файловое хранилище:** `file-streams.limits.profile: MEDIA` → один ObjectStreams и S3-клиент обслуживают исходники и результат. [Конфигурация](examples/secured-video/src/main/resources/application.yml), [владение ресурсами](docs/0.1.0/ru.md#storage-and-lifecycle).
3. **Выдавать разрешённые сегменты:** `assets.handle(exchange, sourceKey, generation, name)` → существующий streaming/range после проверки прав. [Контроллер](examples/secured-video/src/main/java/example/video/VideoController.java), [воспроизведение](docs/0.1.0/ru.md#playback).
4. **Ограничить подготовку:** `video-streams.max-concurrent: 1` → предел активной подготовки без собственного executor. Defaults также ограничивают байты, число сегментов и время. [Настройки](docs/0.1.0/reference.ru.md).
5. **Связать React-загрузку и просмотр:** [video-streams-react](https://github.com/dlorian59/video-streams-react) использует тот же ID и prepare-маршрут. Commit загрузки и готовность видео — разные состояния. [Полный поток](docs/0.1.0/ru.md#browser-flow).

V1 формирует один H.264/AAC fMP4 HLS-вариант, не лестницу адаптивных качеств и не DRM. FFmpeg не поставляется внутри пакета. Отмена останавливает принадлежащие операции, но не откатывает хранилище; потерянный ответ при финальной публикации оставляет неизвестный исход. [Границы и диагностика](docs/0.1.0/ru.md#failures).

[Полное руководство](docs/0.1.0/ru.md) · [API/defaults](docs/0.1.0/reference.ru.md) · [Разработка](CONTRIBUTING.md) · [Проверки](docs/VERIFICATION.md)

</details>

[MIT](LICENSE). External FFmpeg/player licenses remain their own. Gradle wrapper: [Apache-2.0](gradle/licenses/LICENSE), [notice](gradle/licenses/NOTICE).
