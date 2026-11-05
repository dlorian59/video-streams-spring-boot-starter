package io.github.alex59.videostreams.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.github.alex59.videostreams.VideoFailure;
import java.util.concurrent.atomic.AtomicReference;

import io.github.alex59.filestreams.ObjectStreams;
import io.github.alex59.filestreams.StreamLimits;
import io.github.alex59.filestreams.StreamProfile;
import io.github.alex59.filestreams.boot.FileStreamsAutoConfiguration;
import io.github.alex59.videostreams.VideoOptions;
import io.github.alex59.videostreams.VideoStreams;
import io.github.alex59.videostreams.webflux.VideoAssetHandler;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

class AutoConfigurationTest {
    @TempDir Path workspace;

    private ReactiveWebApplicationContextRunner runner(ObjectStreams storage) {
        return new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FileStreamsAutoConfiguration.class, VideoStreamsAutoConfiguration.class))
                .withBean(ObjectStreams.class, () -> storage)
                .withPropertyValues("file-streams.enabled=true", "file-streams.limits.profile=MEDIA",
                        "video-streams.work-directory=" + workspace);
    }

    @Test void importsAreDiscoveredButVideoAndHttpExposureAreOptIn() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(VideoStreamsAutoConfiguration.class.getName());
        var storage = mock(ObjectStreams.class);
        runner(storage).run(context -> {
            assertThat(context).doesNotHaveBean(VideoStreams.class).doesNotHaveBean(VideoAssetHandler.class);
            assertThat(context).doesNotHaveBean("fileStreamsHandlerMapping");
        });
        verifyNoInteractions(storage);
    }

    @Test void sharesStorageAndProfileWithFileStarterWithoutInstallingRoutesOrStartingWork() {
        var storage = mock(ObjectStreams.class);
        var managed = new AtomicReference<VideoStreams>();
        runner(storage).withPropertyValues("video-streams.enabled=true", "video-streams.max-concurrent=1")
                .run(context -> {
                    assertThat(context).hasSingleBean(VideoStreams.class).hasSingleBean(VideoAssetHandler.class);
                    assertThat(context.getBean(ObjectStreams.class)).isSameAs(storage);
                    assertThat(context.getBean(StreamLimits.class)).isEqualTo(StreamProfile.MEDIA.limits());
                    assertThat(context.getBean(VideoOptions.class).maxConcurrent()).isEqualTo(1);
                    managed.set(context.getBean(VideoStreams.class));
                    assertThat(context).doesNotHaveBean("fileStreamsHandlerMapping");
                });
        assertThat(assertThrows(VideoFailure.class, () -> managed.get().prepare("demo:after-close").block()).code())
                .isEqualTo(VideoFailure.Code.CLOSED);
        verifyNoInteractions(storage);
    }

    @Test void suppliedBeansWinAndInvalidProcessingBudgetsFailAtBindingStartup() {
        var storage = mock(ObjectStreams.class);
        var suppliedOptions = VideoOptions.defaults(workspace);
        var suppliedHandler = new VideoAssetHandler(storage, StreamProfile.MEDIA.limits());
        runner(storage).withPropertyValues("video-streams.enabled=true")
                .withBean(VideoOptions.class, () -> suppliedOptions)
                .withBean(VideoAssetHandler.class, () -> suppliedHandler)
                .run(context -> {
                    assertThat(context.getBean(VideoOptions.class)).isSameAs(suppliedOptions);
                    assertThat(context.getBean(VideoAssetHandler.class)).isSameAs(suppliedHandler);
                });
        for (String invalid : new String[] {"video-streams.max-concurrent=0", "video-streams.max-input-size=0B", "video-streams.timeout=-1s"})
            runner(storage).withPropertyValues("video-streams.enabled=true", invalid)
                    .run(context -> assertThat(context).hasFailed());
        verifyNoInteractions(storage);
    }
}
