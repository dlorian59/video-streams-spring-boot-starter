package io.github.alex59.videostreams.boot;

import io.github.alex59.videostreams.VideoOptions;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Optional overrides of core defaults. Enabling preparation never exposes an HTTP route.
 * @param enabled whether video auto-configuration is active
 * @param workDirectory private bounded processing workspace
 * @param ffmpegCommand executable name or path, not a shell command line
 * @param ffprobeCommand executable name or path, not a shell command line
 * @param timeout total preparation deadline
 * @param maxInputSize maximum source bytes
 * @param maxOutputSize maximum prepared bytes
 * @param maxSegments maximum media segment count
 * @param maxConcurrent maximum active preparations
 */
@ConfigurationProperties("video-streams")
public record VideoStreamsProperties(
        @DefaultValue("false") boolean enabled,
        Path workDirectory, String ffmpegCommand, String ffprobeCommand,
        Duration timeout, DataSize maxInputSize, DataSize maxOutputSize,
        Integer maxSegments, Integer maxConcurrent) {

    /**
     * Resolves omitted settings from the core profile and validates all explicit overrides.
     * @return immutable options for the managed service
     */
    public VideoOptions options() {
        Path directory = workDirectory == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "video-streams") : workDirectory;
        var defaults = VideoOptions.defaults(directory);
        return new VideoOptions(directory,
                ffmpegCommand == null ? defaults.ffmpegCommand() : ffmpegCommand,
                ffprobeCommand == null ? defaults.ffprobeCommand() : ffprobeCommand,
                timeout == null ? defaults.timeout() : timeout,
                maxInputSize == null ? defaults.maxInputBytes() : maxInputSize.toBytes(),
                maxOutputSize == null ? defaults.maxOutputBytes() : maxOutputSize.toBytes(),
                maxSegments == null ? defaults.maxSegments() : maxSegments,
                maxConcurrent == null ? defaults.maxConcurrent() : maxConcurrent);
    }
}
