# 0.1.0 — From an uploaded file to protected playback

[Versions](../index.md) · [English](en.md) · [Русский](ru.md) · [README](../../README.md)

An upload is ready to store; a video needs a playback format. Keep these two operations separate: ordinary file-streams commits one continuous source stream, then video-streams prepares one HLS VOD generation. The browser can subsequently request a playlist and individual media segments instead of assembling the complete source as a Blob.

Start with the [existing-project integration](../../README.md). This chapter explains decisions and failure behavior using the [complete secured consumer](../../examples/secured-video). Maven and Gradle consumer builds are included; local artifact preparation and actual launch commands belong in [development](../../CONTRIBUTING.md).

## Before connecting it

You need Java 17+, Boot 4.1.2 with WebFlux, an application-owned ObjectStreams implementation (normally the file-streams S3 integration), and FFmpeg/ffprobe executables. Core output is a single H.264/AAC fMP4 HLS VOD rendition: fixed 1280×720 fit, 30 fps and roughly two-second media segments. It is not live streaming, ABR quality selection, DRM or a general FFmpeg command executor.

Give the process a private writable work directory with disk and CPU quotas appropriate to concurrent preparation. The input is downloaded to a bounded local file before encoding; playback does not begin while the source upload is in progress. No large source Blob is created in the browser, but preparation still requires temporary server disk. Dependencies do not install FFmpeg or silently create a public route.

<a id="storage-and-lifecycle"></a>
## One storage client, explicit lifecycle

Enable file-streams and keep file-streams.http.enabled=false. Select its MEDIA limit profile to align the raw upload limit with the video default of 512 MiB. video-streams.enabled=true adds VideoOptions, VideoStreams and, in a reactive web application with StreamLimits, VideoAssetHandler. Existing beans take precedence. A single ObjectStreams candidate is required; select one with @Primary when your application defines several.

The [auto-configuration](../../src/main/java/io/github/alex59/videostreams/boot/VideoStreamsAutoConfiguration.java) reuses the existing ObjectStreams; it does not construct another S3 engine. Spring closes the VideoStreams bean it creates on shutdown, cancelling its owned work. The core never closes the supplied ObjectStreams/client. File-streams or your application manages that client's lifecycle.

Keep your AWS credentials provider and secret configuration. The complete example uses a configurable S3 endpoint for isolated testing, but no credentials are committed in source. Bucket permissions and storage lifecycle remain application/deployment choices. A queue is optional: if preparation must survive a request disconnect or process restart, call the same prepare method from your application's durable job system and persist the result there.

<a id="preparation"></a>
## Upload first, prepare second

The [controller](../../examples/secured-video/src/main/java/example/video/VideoController.java) demonstrates these steps:

1. The authenticated account selects a new source ID such as demo:training-2026. The namespace portion is checked against the actual Principal, not an account header from the client.
2. PUT /api/video-sources/{encodedId} streams the source through ObjectStreamHandler. Wait for HTTP 201 and committed=true before preparing it. The returned key equals the ID supplied by the browser.
3. POST /api/videos/{encodedId}/prepare sends {"sourceKey":"demo:training-2026"}. The body must agree with the route and the route must pass authorization before VideoStreams is called.
4. The [service](../../examples/secured-video/src/main/java/example/video/VideoService.java) returns videos.prepare(sourceKey), mapped to the public result. WebFlux owns the subscription; no manual subscribe, block, executor or scheduling annotation is needed.
5. A successful HTTP 201 returns status=ready, a UUID generation, relative playlistUrl, durationSeconds and segmentCount. Persist the result in your domain model when it needs to survive page reloads. The sample returns it directly; it does not create an implicit catalog.

The core validates all generated files and stores init/media objects before publishing playlist.m3u8. A ready response therefore describes a completed generation. Each prepare is a new operation and can produce a new generation; repeating it is not a deduplicated retry. A synchronous request is convenient for modest clips but requires compatible reverse-proxy/request timeouts. For long jobs, a durable queue should own retries, idempotency and result metadata explicitly.

### Source changes and versions

Use a new immutable ID for each source or maintain versioned source metadata. The example checks account ownership but does not implement a write-once source registry. Reusing an ID for a different upload changes what a future prepare reads. A concurrent overwrite is an application race; the starter cannot infer which business version was intended. Existing prepared generations are independent immutable outputs.

A request cancellation stops the subscribed work where possible. Cancellation during playlist publication can race with a successful storage commit. A lost HTTP response can therefore mean an existing playable generation whose metadata the client did not receive. Do not show automatic rollback or retry preparation automatically.

<a id="playback"></a>
## Authorize every playback request

The returned URL has the form /api/videos/{encodedId}/assets/{generation}/playlist.m3u8. The manifest contains relative init.mp4 and segment-NNNNNN.m4s references; they inherit the same authenticated route and generation.

[VideoController.asset](../../examples/secured-video/src/main/java/example/video/VideoController.java) checks account ownership on every request before delegating to VideoAssetHandler. The handler resolves only allowlisted artifact names and reuses continuous response/range handling from file-streams. A UUID is not permission. The sample ownership rule allows an account to read all generations of its source; add domain ACL/revocation rules if that is too broad.

No generic /objects/** route is enabled. This matters because exposing physical storage keys through a second route could bypass the media controller's authorization. Do not place bearer credentials in a playlist URL. Configure authenticated/private cache behavior and TLS in your host; sharing a cached response across accounts is not a feature of this starter.

<a id="browser-flow"></a>
## Connect upload and playback

The [video-streams-react integration](https://github.com/dlorian59/video-streams-react) uses the file upload component and a separate prepare URL. Configure objectsUrl=/api/video-sources, objectKey=demo:&lt;new-id&gt;, and prepareUrl=/api/videos/{encodedId}/prepare. The prepared playlistUrl goes to the HLS player; no URL rebuilding or source-Blob download belongs in application UI code.

Use the same-origin authenticated transport for upload, preparation, playlist and segments. The complete consumer provides [DemoSecurity](../../examples/secured-video/src/main/java/example/video/DemoSecurity.java) only to make the example executable. Existing applications retain their own security system.

The example login sequence is GET /api/session → POST /login form-urlencoded with username/password plus the returned CSRF header → GET /api/session again. The response supplies username, csrfHeader and csrfToken. Authentication lives in an HttpOnly secure session cookie; the browser includes it automatically. Send the current CSRF header on unsafe upload/prepare requests. Protected GETs also require the cookie. The CSRF token is request data, not a replacement for authentication.

The demo frontend can proxy /api to the TLS/HTTP2 backend to stay same-origin. Native request-body streaming requires browser/protocol support; the file component reports unsupported transport rather than buffering the complete source. V1 uses MSE/hls.js and reports unsupported playback where that engine is unavailable. Native HLS integration is outside this version; it would require separate host integration and compatible authentication. [React executable example](https://github.com/dlorian59/video-streams-react/tree/main/examples).

<a id="failures"></a>
## Bound work and understand failures

Defaults are three minutes per complete preparation, 512 MiB input and output, 300 media segments and two concurrent preparations. Lower max-concurrent when CPU or disk is constrained. Configure upload, object-transfer and preparation limits together: successful source storage does not promise successful preparation. See [all properties](reference.en.md).

- INVALID_SOURCE/400: source or media structure is unsuitable. Fix the input; repeating the same invalid file is not recovery.
- TOO_LARGE/413: one processing bound was exceeded. Choose suitable limits/resources or a smaller input; do not silently truncate the video.
- BUSY/429: the service's active preparation limit is occupied. The caller decides when to submit another operation; the starter has no hidden wait queue.
- PROCESS_FAILED/502: inspect server-side process diagnostics and executable availability. Do not return local paths/raw FFmpeg output to clients.
- TIMEOUT/504, STORAGE_FAILED/502 or a disconnected request: an output may have committed without its acknowledgment. The example says the result is unknown, not rolled back.
- CLOSED/CANCELLED/503: the service is closing or work was cancelled. A retry is a new preparation, not a resumption.

The [error adapter](../../examples/secured-video/src/main/java/example/video/VideoErrors.java) exposes categories without raw process/storage diagnostics. Core bounds/process termination reduce resource leaks but do not replace host CPU/disk isolation. ObjectStreams has no deletion API: failed preparations may leave unreferenced immutable artifacts. Configure storage retention/cleanup separately; never delete a potentially committed manifest merely because its response was lost.

## What to retain

Source commit, video readiness and playback are different states. One storage boundary serves both files and video artifacts. Access checks remain in the host and apply to every request. A generation makes outputs immutable, not exactly-once preparation. Defaults remove lifecycle code; explicit limits, credentials, authorization and durable job policy still reflect the application's requirements.

[Spring Security CSRF](https://docs.spring.io/spring-security/reference/reactive/exploits/csrf.html) explains the session/token behavior reused by the example. [Spring Boot HTTP/2](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.configure-http2) documents the TLS/HTTP2 server configuration. [README](../../README.md) · [Reference](reference.en.md) · [Verification](../VERIFICATION.md)
