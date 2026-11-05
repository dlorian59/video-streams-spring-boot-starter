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
