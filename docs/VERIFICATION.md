# Verification / Проверки — 0.1.0

[README](../README.md) · [Versions](index.md) · [English guide](0.1.0/en.md) · [Русское руководство](0.1.0/ru.md)

## Local evidence, 2026-11-06

The actual consumer used Java 17.0.21, Spring Boot 4.1.2, FFmpeg/ffprobe 6.1.1-3ubuntu5 and an isolated Garage 2.4.1 S3-compatible server. Browser evidence used Chromium 156.0.8078.4 with Playwright 1.64.1. No existing application storage or desktop browser session was used.

- **Starter build: passed.** Three meaningful auto-configuration scenarios check explicit enablement, import discovery, shared ObjectStreams, no generic HTTP route, properties/defaults, user bean backoff and actual managed VideoStreams closure. Invalid resource budgets fail startup. No failed/skipped tests.
- **Packaging: passed.** JAR bytecode is major 61 (Java 17). Sources, Javadoc, MIT license, Spring auto-configuration imports, configuration metadata and Maven POM/module are produced. Registry publication is not claimed.
- **Local dependency bootstrap: passed.** The provided script prepared all four sibling repositories into their local artifact directories and Maven Local. No source clone or remote publication is performed by the script.
- **Independent consumers: passed.** The Maven build produced a runnable Boot JAR; the independent Gradle build compiled the same complete application. After a clean Maven package, the nested core/WebFlux JAR hashes were checked against the final local artifacts before JVM startup. All HTTP and both-language browser checks below were then repeated on that verified JAR. [Artifact provenance](evidence/packaged-artifacts.json).
- **Real HTTP → S3 → FFmpeg → HLS: passed.** The [consumer verification](../examples/secured-video/verify.py) uploaded a 7,374,595-byte synthetic 32-second video, obtained 201 committed, prepared a 16-segment generation and received 201 ready. It read the manifest, init and a media segment, and checked a 64-byte range with 206. [Recorded result](evidence/http-consumer.json).
- **Actual security boundaries: passed.** Anonymous media GET returns 401, foreign source and asset access return 403, prepare without CSRF returns 403, a sourceKey/body mismatch returns 400, /objects/** stays absent (404), and a non-allowlisted artifact name returns 400. Authentication is a real Spring session with CSRF, not a mocked principal in the end-to-end run.
- **React full flow, both EN and RU: passed.** HTTPS/HTTP2 streaming upload → protected Spring prepare → real S3 artifacts → HLS playback. Initial preload requested 3 of 16 media segments; playback advanced, seeking reached 24 seconds, teardown stopped further requests, and clearing the session made playlist access return 401. No recorded browser errors. [Browser result](evidence/browser-consumer.json).

Actual paired recordings live with the React example: [EN GIF](https://github.com/dlorian59/video-streams-react/blob/main/assets/visuals/attachment-spring-en.gif), [EN PNG](https://github.com/dlorian59/video-streams-react/blob/main/assets/visuals/attachment-spring-en.png), [RU GIF](https://github.com/dlorian59/video-streams-react/blob/main/assets/visuals/attachment-spring-ru.gif), [RU PNG](https://github.com/dlorian59/video-streams-react/blob/main/assets/visuals/attachment-spring-ru.png).

A packaging check caught Maven reusing an older repackaged Boot JAR while an unreleased dependency kept the same 0.1.0 version. Development/CI now use clean package, local preparation refreshes Maven Local, and the evidence was regenerated only after nested-JAR hashes matched.

The real server correctly rejected an invalid Range header sent by an early browser loader; the browser owner fixed the zero-offset/default-range interpretation and repeated the complete flow against this server. The successful evidence above is from that corrected run.

The owned Boot/Garage processes were stopped after the browser completed; private fixture data, credentials and temporary TLS keys were removed. The shared Garage executable was left untouched. No production S3 bucket, existing Docker service or private application source was modified. The damaged shared Docker store was not repaired; the fixture used the standalone Garage binary.

## Limits

The supplied CI workflow prepares source dependencies and verifies the starter plus both consumer builds; remote CI execution is not asserted here. Java 21/25/27, other browser engines, native HLS and distributed deployment failover were not exercised in this starter task. Video core tests separately cover cancellation and process cleanup; the starter checks lifecycle delegation and the real normal pipeline, not every failure injection against S3.

The Python verifier deliberately buffers its small synthetic source; it is not evidence of browser request streaming. The separate actual HTTPS/HTTP2 React run supplies that evidence. Initial 3/16 segment requests are an observation for this fixture/configuration, not a browser-wide hard memory or bandwidth guarantee.

Single-rendition HLS, no live/ABR/DRM; no automatic durable queue, source version catalog or orphan deletion. Ownership checks do not make source overwrite race-free. A timeout or lost final-publication response may leave committed media. Host authorization, immutable source policy, storage durability, cleanup, process isolation and durable jobs remain explicit deployment/application responsibilities.

## Русский

Проверено локально 2026-11-06: Java 17.0.21, Boot 4.1.2, FFmpeg/ffprobe 6.1.1-3ubuntu5, отдельный Garage 2.4.1. Браузер — Chromium 156.0.8078.4 / Playwright 1.64.1. Существующие данные приложений и пользовательский браузер не использовались.

Три модульных сценария стартера прошли без ошибок/пропусков: явное включение, imports, общий ObjectStreams, отсутствие общего HTTP-маршрута, defaults/валидация, приоритет beans и настоящее закрытие управляемого VideoStreams. Байткод — Java 17; JAR содержит MIT и Spring metadata. Подготовка локальных артефактов всех четырёх соседних проектов выполнена. Maven собрал запускаемый Boot JAR, независимый Gradle собрал те же исходники. После clean package проверено совпадение hash вложенных core/WebFlux JAR с финальными артефактами до старта JVM. Затем на проверенном JAR повторены все HTTP-проверки и оба языковых browser-прогона. [Происхождение артефактов](evidence/packaged-artifacts.json).

Реальный путь HTTP → S3 → FFmpeg → HLS прошёл на синтетическом видео 7 374 595 байт / 32 секунды: upload201, prepare201, 16 сегментов, плейлист/init/сегмент и range206. Отдельно проверены anonymous401, чужой source/asset403, отсутствие CSRF403, несовпадение sourceKey400, закрытый общий маршрут404 и недопустимое имя артефакта400. [HTTP-результат](evidence/http-consumer.json).

В парном React-примере EN и RU выполнена настоящая потоковая HTTPS/HTTP2-загрузка и просмотр: сначала запрошено 3 из 16 медиасегментов, время воспроизведения увеличивается, seek достигает 24 секунд, teardown останавливает запросы, сброс сессии даёт playlist401. Ошибок браузера нет. [Результат](evidence/browser-consumer.json); GIF и PNG по ссылкам выше показывают реальные прогоны. Проверка упаковки обнаружила старый Boot JAR после обычного package при обновлении зависимости без смены локальной версии 0.1.0. Команды заменены на clean package, Maven Local обновляется, доказательства заново получены после сверки вложенных JAR. Найденная неверная передача Range в раннем browser loader исправлена владельцем, после чего полный сценарий повторён.

После завершения браузера остановлены только свои Boot/Garage, удалены временные данные, credentials и TLS-ключи. Общий Garage executable не удалялся; повреждённый Docker store не ремонтировался. Удалённая CI, JVM 21/25/27, другие браузеры, native HLS и отказ распределённого deployment здесь не проверялись. Python-клиент буферизует небольшой тестовый исходник; доказательство браузерного стрима даёт отдельный React-прогон. 3/16 — наблюдение для этой конфигурации, не общая гарантия памяти/трафика.

Гарантии не расширены до live/ABR/DRM, долговечной очереди, версионирования исходника или автоматического удаления артефактов. Потерянный ответ при финальной публикации может скрывать успешный commit; авторизация, неизменяемые исходники, storage/CPU/disk-политики и надёжные jobs принадлежат приложению.
