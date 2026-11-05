# 0.1.0 — Starter reference

[Versions](../index.md) · [Guide](en.md) · [English](reference.en.md) · [Русский](reference.ru.md) · [README](../../README.md)

## Properties

All keys below have the prefix video-streams. Omitted values are resolved from VideoOptions.defaults, not a separate processing profile.

| Key | Default | Meaning |
|---|---|---|
| enabled | false | Opt-in auto-configuration. Does not expose routes. |
| work-directory | java.io.tmpdir/video-streams | Private temporary preparation directory; set explicitly for deployment. |
| ffmpeg-command | ffmpeg | Executable name/path, not arbitrary shell text. |
| ffprobe-command | ffprobe | Executable name/path for metadata validation. |
| timeout | 3m | Whole preparation budget; positive, no more than 1h. |
| max-input-size | 512MB | Source byte bound. Positive Spring DataSize. |
| max-output-size | 512MB | Prepared byte bound. Positive Spring DataSize. |
| max-segments | 300 | Media artifact count, 1..10000. |
| max-concurrent | 2 | Active preparation admission, 1..64. No implicit durable queue. |

Spring DataSize MB is binary MiB (512MB = 536870912 bytes). These are object/process bounds, not a hard JVM heap or operating-system disk quota. Core also validates individual artifact sizes and media structure. The fixed media profile is defined by video-streams; arbitrary FFmpeg flags/filters/URLs are not exposed by configuration.

[Properties source](../../src/main/java/io/github/alex59/videostreams/boot/VideoStreamsProperties.java) · [Core options and guarantees](https://github.com/dlorian59/video-streams)

## Beans and ownership

- VideoOptions: immutable resolved configuration. A supplied bean overrides automatic settings.
- VideoStreams: created when enabled and a single ObjectStreams candidate exists. Uses prepare(String sourceKey), returning Mono of VideoAsset. The created bean is closed by Spring; core close waits up to six seconds for owned cleanup and can fail if cleanup remains incomplete. Supplied storage remains externally owned. A user VideoStreams bean wins.
- VideoAssetHandler: created for a reactive web application with ObjectStreams and StreamLimits. handle(exchange, sourceKey, generation, name) serves a host-authorized artifact. A user bean wins.
- VideoStreamsProperties: bound only while this auto-configuration is enabled. options() delegates validation to the core constructor.

[Auto-configuration source](../../src/main/java/io/github/alex59/videostreams/boot/VideoStreamsAutoConfiguration.java). No handler mapping, security chain, scheduler annotation, task database, storage client or public controller is created by the video starter. file-streams configuration controls its own client and optional generic HTTP exposure; leave that generic exposure disabled when using application-authorized routes.

## Consumer contract

[Controller](../../examples/secured-video/src/main/java/example/video/VideoController.java), [service](../../examples/secured-video/src/main/java/example/video/VideoService.java), [error mapping](../../examples/secured-video/src/main/java/example/video/VideoErrors.java).

- PUT /api/video-sources/{id}: raw request body; source ID equals upload result key. HTTP 201 {committed,key,bytes,etag} follows storage commit.
- POST /api/videos/{id}/prepare: JSON {sourceKey:id}; both route ownership and equality are checked. HTTP 201 {status:"ready",generation,playlistUrl,durationSeconds,segmentCount} after generation publication. No 202 state or polling endpoint.
- GET /api/videos/{id}/assets/{generation}/{name}: owner check every time; strict UUID and allowlisted artifact names. Full/range responses reuse file-streams.
- GET /api/session and POST /login exist only in the standalone demo, not the starter. Session credentials stay in a secure HttpOnly cookie; unsafe operations require CSRF. Existing applications retain their own security.

The demo namespace format is account:source, using URL-safe components. An account may access its historical generations. The example does not persist a video catalog, enforce write-once source uploads, implement an outbox/job queue, or authorize sharing between accounts. These domain rules belong to your host.

## Compatibility and failure boundaries

Baseline Java 17; no preview APIs or required virtual threads. Newer supported application runtimes can use the same artifact; actual checks are listed in [verification](../VERIFICATION.md), without implying a matrix was executed. Boot 4.1.2/WebFlux is the demonstrated integration; WebMVC controllers are not provided.

FFmpeg/ffprobe are external runtime prerequisites and retain their own licenses. The browser integration uses MSE/hls.js. No ABR ladder, live pipeline, DRM, native-HLS wrapper or public CDN policy is included. Process cancellation is not storage rollback; a lost final-publication acknowledgment is ambiguous. Unreferenced parts need an external retention policy because ObjectStreams has no delete API.

[Guide](en.md) · [Development](../../CONTRIBUTING.md) · [README](../../README.md)
