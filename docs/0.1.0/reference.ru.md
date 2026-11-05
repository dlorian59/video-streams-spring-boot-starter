# 0.1.0 — Справочник стартера

[Версии](../index.md) · [Руководство](ru.md) · [English](reference.en.md) · [Русский](reference.ru.md) · [README](../../README.md)

## Настройки

Все ключи имеют префикс video-streams. Пропущенные значения берутся из VideoOptions.defaults, а не из отдельного профиля обработки.

| Ключ | Default | Значение |
|---|---|---|
| enabled | false | Явно включает автоконфигурацию, не маршруты. |
| work-directory | java.io.tmpdir/video-streams | Закрытый временный каталог; в deployment задавайте явно. |
| ffmpeg-command | ffmpeg | Имя/путь executable, не произвольный shell-текст. |
| ffprobe-command | ffprobe | Исполняемый файл проверки метаданных. |
| timeout | 3m | Бюджет всей подготовки: положительный, не более 1h. |
| max-input-size | 512MB | Предел входных байтов, положительный Spring DataSize. |
| max-output-size | 512MB | Предел подготовленных байтов, положительный Spring DataSize. |
| max-segments | 300 | Число медиасегментов, 1..10000. |
| max-concurrent | 2 | Допуск активных подготовок, 1..64. Скрытой долговечной очереди нет. |

MB в Spring DataSize — двоичные МиБ (512MB = 536870912 байт). Это ограничения объектов/операций, не жёсткая квота heap JVM или диска ОС. Core также проверяет размеры отдельных артефактов и структуру медиа. Фиксированный профиль задаётся video-streams; произвольные FFmpeg flags/filters/URLs не передаются через конфигурацию.

[Исходник настроек](../../src/main/java/io/github/alex59/videostreams/boot/VideoStreamsProperties.java) · [Core и гарантии](https://github.com/dlorian59/video-streams)

## Beans и владение

- VideoOptions: неизменяемая конфигурация. Собственный bean заменяет автоматическую настройку.
- VideoStreams: создаётся при enabled и однозначном ObjectStreams. prepare(String sourceKey) возвращает Mono с VideoAsset. Созданный bean закрывает Spring; close core ждёт завершения своей очистки до шести секунд и может завершиться ошибкой при незаконченной очистке. Хранилище остаётся внешним. Собственный VideoStreams имеет приоритет.
- VideoAssetHandler: создаётся в reactive web-приложении с ObjectStreams и StreamLimits. handle(exchange, sourceKey, generation, name) выдаёт разрешённый приложением артефакт. Собственный bean имеет приоритет.
- VideoStreamsProperties: связывается при включённой автоконфигурации. options() делегирует проверку конструктору core.

[Автоконфигурация](../../src/main/java/io/github/alex59/videostreams/boot/VideoStreamsAutoConfiguration.java). Video starter не создаёт handler mapping, security chain, аннотацию планирования, базу задач, storage client или публичный контроллер. Конфигурация file-streams управляет собственным клиентом и необязательной общей HTTP-выдачей; оставьте её выключенной при маршрутах приложения с проверкой прав.

## Контракт потребителя

[Контроллер](../../examples/secured-video/src/main/java/example/video/VideoController.java), [сервис](../../examples/secured-video/src/main/java/example/video/VideoService.java), [ошибки](../../examples/secured-video/src/main/java/example/video/VideoErrors.java).

- PUT /api/video-sources/{id}: сырое тело; ID исходника совпадает с key результата. HTTP 201 {committed,key,bytes,etag} после commit хранилища.
- POST /api/videos/{id}/prepare: JSON {sourceKey:id}; проверяются владелец маршрута и совпадение ключа. HTTP 201 {status:"ready",generation,playlistUrl,durationSeconds,segmentCount} после публикации. Нет 202 и endpoint опроса.
- GET /api/videos/{id}/assets/{generation}/{name}: проверка владельца всегда; UUID и имена артефактов валидируются. Полная/range-выдача переиспользует file-streams.
- GET /api/session и POST /login относятся только к отдельному демо, не к стартеру. Сессия находится в secure HttpOnly cookie, небезопасным операциям нужен CSRF. Приложение сохраняет свою security-систему.

Демонстрационный namespace — account:source с URL-safe частями. Аккаунт читает свои исторические поколения. Пример не хранит каталог видео, не обеспечивает write-once исходники, не реализует outbox/job queue и не задаёт совместный доступ аккаунтов. Это доменные правила приложения.

## Совместимость и границы отказов

Минимальная Java 17, без preview и обязательных virtual threads. Один артефакт подходит новым поддерживаемым runtime приложения; фактические прогоны указаны в [отчёте](../VERIFICATION.md), без обещания выполненной матрицы. Продемонстрирован Boot 4.1.2/WebFlux; WebMVC-контроллеры не предоставляются.

FFmpeg/ffprobe — внешние runtime-зависимости со своими лицензиями. Браузерная интеграция использует MSE/hls.js. Нет ABR-лестницы, live pipeline, DRM, native-HLS wrapper или политики публичного CDN. Отмена процесса не откатывает хранилище; потерянное подтверждение финальной публикации неоднозначно. Несвязанные части требует очищать внешняя политика: ObjectStreams не имеет delete API.

[Руководство](ru.md) · [Разработка](../../CONTRIBUTING.md) · [README](../../README.md)
