package co.digikool.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.thymeleaf.spring6.context.webflux.IReactiveDataDriverContextVariable;
import org.thymeleaf.spring6.context.webflux.ReactiveDataDriverContextVariable;
import reactor.core.publisher.Mono;

import java.util.Map;

@Controller
public class WelcomeController {
    
    private static final Logger logger = LoggerFactory.getLogger(WelcomeController.class);
    
    /**
     * Welcome page shown after successful login
     */
    @GetMapping("/")
    public Mono<Void> home(
            @AuthenticationPrincipal OAuth2User principal,
            ServerWebExchange exchange) {

        if (principal == null) {
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().set("Location", "/oauth2/authorization/gateway-client");
            return exchange.getResponse().setComplete();
        }

        // Otherwise return some response or view
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().set("Location", "/welcome");
        return exchange.getResponse().setComplete();
    }

    @GetMapping("/welcome")
    public String welcomePage(@AuthenticationPrincipal OAuth2User principal, Model model) {
        model.addAttribute("userContext", principal != null ? principal.getAttributes() : Map.of());
        return "welcome";
    }
}