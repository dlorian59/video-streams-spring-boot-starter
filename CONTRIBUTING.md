# Development and consumer verification / Разработка и проверка потребителя

[README](README.md) · [English guide](docs/0.1.0/en.md) · [Русское руководство](docs/0.1.0/ru.md)

## English

The artifacts are not published to Maven Central. Keep four source checkouts as siblings: file-streams, file-streams-spring-boot-starter, video-streams and video-streams-spring-boot-starter. JDK 17+, FFmpeg with the H.264/AAC encoders, ffprobe and a writable work directory are required. The scripts build only local repositories; they do not fetch source or publish remotely.

From this repository:

```bash
./scripts/prepare-local-artifacts.sh
./gradlew build
cd examples/secured-video
mvn -q clean package
../../gradlew classes
```

The preparation script refreshes each checkout's build/local-repository and Maven Local, matching both consumers and avoiding stale local artifacts during development. GRADLE_COMMAND can optionally select an installed Gradle binary. Unit checks validate opt-in behavior, no default media routes, user bean precedence, configuration validation, shared storage and managed service shutdown. CI performs source-artifact preparation plus Maven and Gradle consumer compilation; real S3/FFmpeg/browser checks are reported separately, not implied by compilation.

### Configure an isolated environment

Use a disposable S3-compatible bucket/account, not production data. Set VIDEO_BUCKET, AWS_REGION, S3_ENDPOINT and the standard AWS credentials-provider settings. The example reads DEMO_PASSWORD for the fixed local username demo. This test-only login is not an authentication system to copy into a production service.

Generate a temporary certificate and sample media outside the repository. Choose a disk with enough space; VIDEO_DEMO_DIR is an example path under a caller-selected work disk:

```bash
export VIDEO_DEMO_DIR=/path/to/private/video-demo
mkdir -p "$VIDEO_DEMO_DIR"
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$VIDEO_DEMO_DIR/tls-key.pem" -out "$VIDEO_DEMO_DIR/tls-cert.pem" \
  -days 2 -subj '/CN=127.0.0.1' -addext 'subjectAltName=IP:127.0.0.1,DNS:localhost'
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 32 \
  -c:v libx264 -threads 1 -preset ultrafast -pix_fmt yuv420p -c:a aac -b:a 96k \
  -movflags +faststart "$VIDEO_DEMO_DIR/source-32s.mp4"
export VIDEO_WORK_DIRECTORY="$VIDEO_DEMO_DIR/work"
export DEMO_CERTIFICATE="file:$VIDEO_DEMO_DIR/tls-cert.pem"
export DEMO_PRIVATE_KEY="file:$VIDEO_DEMO_DIR/tls-key.pem"
java -jar target/secured-video-0.1.0.jar
```

The application binds loopback HTTPS19445 and enables HTTP/2. Do not commit the generated private key. On startup the core validates configuration; missing FFmpeg/ffprobe is reported on preparation, not by running arbitrary startup probes.

### Verify actual behavior

In a second terminal with the same disposable environment, from examples/secured-video:

```bash
VIDEO_TEST_SOURCE="$VIDEO_DEMO_DIR/source-32s.mp4" \
VIDEO_TEST_CERTIFICATE="$VIDEO_DEMO_DIR/tls-cert.pem" \
python3 verify.py
```

Expected: JSON result PASS, duration 32 seconds and a nonzero segment count. The script logs in with a session and CSRF, tests unauthorized/foreign-account/CSRF/body mismatch and disabled generic routes, then actually uploads/prepares/reads playlist/init/media and a byte range. It trusts only the supplied test certificate. The small test client buffers its synthetic file; this is not claimed as proof of browser streaming upload.

For the real React upload/player flow, run the [video-streams-react paired example](https://github.com/dlorian59/video-streams-react/tree/main/examples). Use its HTTPS/HTTP2 streaming proxy on 5182 to backend19445, with the same temporary certificate paths. A plain HTTP/1 Vite proxy is insufficient for fetch request-body streaming. Do not replace it with a full-Blob fallback. The proxy must stream both the incoming body and upstream response; do not log session/CSRF credentials.

After verification stop only your owned app/storage processes and remove their temporary data/certificates. Source objects and orphaned generated artifacts are not deleted by this starter. [Evidence and limits](docs/VERIFICATION.md).

## Русский

Артефактов пока нет в Maven Central. Разместите рядом четыре рабочие копии: file-streams, file-streams-spring-boot-starter, video-streams и video-streams-spring-boot-starter. Нужны JDK 17+, FFmpeg с H.264/AAC, ffprobe и рабочий каталог с правом записи. Скрипты готовят только локальные репозитории, не скачивают исходники и не публикуют удалённо.

Из этого репозитория:

```bash
./scripts/prepare-local-artifacts.sh
./gradlew build
cd examples/secured-video
mvn -q clean package
../../gradlew classes
```

Скрипт обновляет build/local-repository каждого checkout и Maven Local, чтобы оба потребителя использовали актуальные локальные артефакты. GRADLE_COMMAND позволяет выбрать установленный Gradle. Модульные проверки охватывают явное включение, отсутствие публичных маршрутов, приоритет собственных beans, валидацию, общее хранилище и закрытие сервиса. CI готовит source artifacts и компилирует Maven/Gradle потребителя; реальные S3/FFmpeg/browser-проверки отражаются отдельно.

### Отдельное окружение

Используйте временный S3-совместимый bucket/account, не рабочие данные. Задайте VIDEO_BUCKET, AWS_REGION, S3_ENDPOINT и стандартную конфигурацию AWS credentials provider. DEMO_PASSWORD задаёт пароль локального пользователя demo. Этот тестовый вход не следует переносить в рабочий сервис как систему аутентификации.

Создайте временный сертификат и медиа вне репозитория. Выберите диск с достаточным местом; VIDEO_DEMO_DIR ниже — пример пути на вашем рабочем диске:

```bash
export VIDEO_DEMO_DIR=/path/to/private/video-demo
mkdir -p "$VIDEO_DEMO_DIR"
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$VIDEO_DEMO_DIR/tls-key.pem" -out "$VIDEO_DEMO_DIR/tls-cert.pem" \
  -days 2 -subj '/CN=127.0.0.1' -addext 'subjectAltName=IP:127.0.0.1,DNS:localhost'
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 32 \
  -c:v libx264 -threads 1 -preset ultrafast -pix_fmt yuv420p -c:a aac -b:a 96k \
  -movflags +faststart "$VIDEO_DEMO_DIR/source-32s.mp4"
export VIDEO_WORK_DIRECTORY="$VIDEO_DEMO_DIR/work"
export DEMO_CERTIFICATE="file:$VIDEO_DEMO_DIR/tls-cert.pem"
export DEMO_PRIVATE_KEY="file:$VIDEO_DEMO_DIR/tls-key.pem"
java -jar target/secured-video-0.1.0.jar
```

Приложение слушает localhost HTTPS19445 с HTTP/2. Private key не должен попадать в git. Core проверяет конфигурацию при создании; отсутствие FFmpeg/ffprobe обнаруживается при prepare, без произвольных startup-команд.

### Проверка реального поведения

Во втором терминале с тем же временным окружением, из examples/secured-video:

```bash
VIDEO_TEST_SOURCE="$VIDEO_DEMO_DIR/source-32s.mp4" \
VIDEO_TEST_CERTIFICATE="$VIDEO_DEMO_DIR/tls-cert.pem" \
python3 verify.py
```

Результат — JSON PASS, длительность 32 секунды и ненулевое число сегментов. Скрипт проходит session/CSRF login, проверяет анонимный/чужой доступ, CSRF, неверное тело, закрытый общий маршрут, затем действительно загружает/готовит/читает плейлист/init/медиа и range. Доверяет только переданному сертификату. Небольшой тестовый клиент буферизует синтетический файл; это не доказательство потоковой загрузки браузера.

Для настоящего React upload/player используйте [парный пример video-streams-react](https://github.com/dlorian59/video-streams-react/tree/main/examples). Его HTTPS/HTTP2 streaming proxy на 5182 передаёт запросы backend19445 с теми же временными сертификатами. Обычный HTTP/1 Vite proxy не подходит fetch request-body streaming. Не заменяйте поток fallback-буферизацией целого Blob. Прокси должен стримить тело и ответ без логирования session/CSRF.

После проверки остановите только свои app/storage-процессы и удалите их временные данные/сертификаты. Стартер не удаляет исходники и несвязанные артефакты. [Доказательства и ограничения](docs/VERIFICATION.md).
