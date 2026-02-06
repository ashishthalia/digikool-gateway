package co.digikool.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    /**
     * Authorization endpoint - initiates OAuth2 flow
     * Redirects to Spring Security's OAuth2 login endpoint which will handle
     * the redirect to the authorization server
     */
    @GetMapping("/authorization")
    public Mono<Void> authorization(ServerHttpResponse response) {
        logger.info("Initiating OAuth2 authorization flow");
        
        // Redirect to Spring Security's OAuth2 login endpoint
        // This will automatically redirect to the authorization server with proper parameters
        response.setStatusCode(org.springframework.http.HttpStatus.FOUND);
        response.getHeaders().set("Location", "/oauth2/authorization/gateway-client");
        
        return response.setComplete();
    }
    
    /**
     * Logout endpoint that clears authentication cookies
     */
    @PostMapping("/logout")
    public Mono<String> logout(ServerHttpResponse response) {
        logger.info("User logout requested");
        
        // Clear authentication cookies
        response.addCookie(ResponseCookie.from("authenticated", "")
                .httpOnly(false)
                .secure(false)
                .sameSite("Strict")
                .maxAge(Duration.ZERO)
                .path("/")
                .build());
                
        response.addCookie(ResponseCookie.from("user_id", "")
                .httpOnly(false)
                .secure(false)
                .sameSite("Strict")
                .maxAge(Duration.ZERO)
                .path("/")
                .build());
                
        response.addCookie(ResponseCookie.from("username", "")
                .httpOnly(false)
                .secure(false)
                .sameSite("Strict")
                .maxAge(Duration.ZERO)
                .path("/")
                .build());
        
        logger.info("Authentication cookies cleared");
        
        return Mono.just("Successfully logged out");
    }
    
    /**
     * Check authentication status
     */
    @GetMapping("/status")
    public Mono<Object> status(@AuthenticationPrincipal org.springframework.security.oauth2.core.user.OAuth2User principal) {
        
        if (principal != null) {
            // Debug logging
            System.out.println("Gateway - OAuth2User principal: " + principal.getName());
            System.out.println("Gateway - All attributes: " + principal.getAttributes());
            System.out.println("Gateway - sub attribute: " + principal.getAttribute("sub"));
            System.out.println("Gateway - name attribute: " + principal.getAttribute("name"));
            System.out.println("Gateway - email attribute: " + principal.getAttribute("email"));
            
            return Mono.just(new Object() {
                public final boolean authenticated = true;
                public final String userId = principal.getAttribute("sub") != null ? principal.getAttribute("sub").toString() : "";
                public final String username = principal.getAttribute("name") != null ? principal.getAttribute("name").toString() : "";
                public final String email = principal.getAttribute("email") != null ? principal.getAttribute("email").toString() : "";
            });
        } else {
            System.out.println("Gateway - No OAuth2User principal found");
            return Mono.just(new Object() {
                public final boolean authenticated = false;
                public final String message = "No authentication token found";
            });
        }
    }

    /**
     * Test user info endpoint directly
     */
    @GetMapping("/test-userinfo")
    public Mono<String> testUserInfo() {
        return org.springframework.web.reactive.function.client.WebClient.builder()
                .build()
                .get()
                .uri("http://localhost:9000/oauth2/userinfo")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> logger.info("User info response: {}", response))
                .onErrorResume(e -> {
                    logger.error("Error calling user info endpoint: {}", e.getMessage());
                    return Mono.just("Error: " + e.getMessage());
                });
    }
}
