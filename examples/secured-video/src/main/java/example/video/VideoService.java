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
