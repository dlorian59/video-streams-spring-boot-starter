package example.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;

// EN: Standalone demo login only. Existing applications retain their own security configuration and identity provider.
// RU: Вход только для отдельного демо. Существующие приложения сохраняют свою security-конфигурацию и провайдера личности.
@Configuration(proxyBeanMethods = false)
public class DemoSecurity {
    @Bean
    MapReactiveUserDetailsService demoUsers(@Value("${demo.password}") String password) {
        if (password.isBlank()) throw new IllegalArgumentException("demo.password must be supplied");
        return new MapReactiveUserDetailsService(User.withUsername("demo")
                .password("{noop}" + password).roles("USER").build());
    }

    @Bean
    SecurityWebFilterChain applicationSecurity(ServerHttpSecurity http) {
        // EN: Keep session-based CSRF enabled for uploads, preparation and login; no endpoint trusts a caller-supplied account ID.
        // RU: CSRF для сессии остаётся включён для загрузки, подготовки и входа; account ID клиента не считается достоверным.
        return http.authorizeExchange(routes -> routes
                    .pathMatchers("/api/session", "/login").permitAll()
                    .anyExchange().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((exchange, error) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }))
                .formLogin(login -> login.authenticationSuccessHandler((web, authentication) -> {
                    web.getExchange().getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                    return web.getExchange().getResponse().setComplete();
                }).authenticationFailureHandler((web, error) -> {
                    web.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return web.getExchange().getResponse().setComplete();
                }))
                .build();
    }
}
