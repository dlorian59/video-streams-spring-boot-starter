package io.github.alex59.videostreams.boot;

import io.github.alex59.filestreams.ObjectStreams;
import io.github.alex59.filestreams.StreamLimits;
import io.github.alex59.filestreams.boot.FileStreamsAutoConfiguration;
import io.github.alex59.videostreams.VideoOptions;
import io.github.alex59.videostreams.VideoStreams;
import io.github.alex59.videostreams.webflux.VideoAssetHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Wires the existing storage and core service; authentication and routes remain application-owned. */
@AutoConfiguration(after = FileStreamsAutoConfiguration.class)
@ConditionalOnProperty(prefix = "video-streams", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(VideoStreamsProperties.class)
public class VideoStreamsAutoConfiguration {
    /** Creates the declarative auto-configuration. */
    public VideoStreamsAutoConfiguration() { }

    @Bean
    @ConditionalOnMissingBean
    VideoOptions videoOptions(VideoStreamsProperties properties) { return properties.options(); }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(ObjectStreams.class)
    VideoStreams videoStreams(ObjectStreams storage, VideoOptions options) {
        return new VideoStreams(storage, options);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(ObjectStreams.class)
    @ConditionalOnBean(StreamLimits.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    VideoAssetHandler videoAssetHandler(ObjectStreams storage, StreamLimits limits) {
        return new VideoAssetHandler(storage, limits);
    }
}
