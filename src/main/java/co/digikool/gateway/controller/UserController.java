package co.digikool.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeController.class);

    @GetMapping("/user/me")
    public Mono<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return Mono.just(Map.of("authenticated", false));
        }

        return Mono.just(Map.of(
                "authenticated", true,
                "username", principal.getAttribute("name"),
                "email", principal.getAttribute("email"),
                "attributes", principal.getAttributes()
        ));
    }

    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        logger.info("Logging out user...");
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().set("Set-Cookie", "JSESSIONID=; Path=/; HttpOnly; Max-Age=0; SameSite=None; Secure");
        return exchange.getResponse().setComplete();
    }
}
