package example.video;

import java.security.Principal;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// EN: Return the request's masked CSRF token; the actual authentication credential stays in an HttpOnly session cookie.
// RU: Возвращаем маскированный CSRF-токен запроса; данные аутентификации остаются в HttpOnly cookie сессии.
@RestController
public class SessionController {
    public record Session(String username, String csrfHeader, String csrfToken) { }

    @GetMapping("/api/session")
    public Mono<Session> session(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        if (token == null) return Mono.error(new IllegalStateException("CSRF support is required"));
        return exchange.getPrincipal().map(Principal::getName).defaultIfEmpty("")
                .zipWith(token, (name, csrf) -> new Session(name, csrf.getHeaderName(), csrf.getToken()));
    }
}
