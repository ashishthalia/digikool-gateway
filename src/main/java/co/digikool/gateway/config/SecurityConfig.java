package co.digikool.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/prometheus", "/error").permitAll()
                        .pathMatchers("/", "/welcome").permitAll()
                        .pathMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .pathMatchers("/authorization").permitAll()
                        // Allow /user/me endpoint - it handles authentication internally
                        .pathMatchers("/user/me").permitAll()
                        // Allow /attendance/** paths - attendance service validates gateway headers
                        .pathMatchers("/attendance/**").permitAll()
                        // Allow OPTIONS requests for CORS preflight (must be before authenticated check)
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // Require authentication for /auth/** API calls (they go through gateway)
                        .pathMatchers("/auth/api/**").authenticated()
                        .anyExchange().authenticated()
                )
                // ✅ Enables the OAuth2 login flow
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler(customAuthenticationSuccessHandler())
                        .authenticationFailureHandler((webFilterExchange, exception) -> {
                            ServerWebExchange exchange = webFilterExchange.getExchange();
                            // Don't redirect OPTIONS requests - let CORS handle them
                            if (exchange.getRequest().getMethod() == org.springframework.http.HttpMethod.OPTIONS) {
                                exchange.getResponse().setStatusCode(HttpStatus.OK);
                                return exchange.getResponse().setComplete();
                            }
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                            DataBuffer buffer = bufferFactory.wrap(
                                    ("Login failed: " + exception.getMessage()).getBytes(StandardCharsets.UTF_8)
                            );
                            return exchange.getResponse().writeWith(Mono.just(buffer));
                        })
                )
                // Handle unauthorized requests - don't redirect API requests or OPTIONS requests
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> {
                            String path = exchange.getRequest().getPath().value();
                            
                            // Don't redirect OPTIONS requests - let CORS handle them
                            if (exchange.getRequest().getMethod() == org.springframework.http.HttpMethod.OPTIONS) {
                                exchange.getResponse().setStatusCode(HttpStatus.OK);
                                return exchange.getResponse().setComplete();
                            }
                            
                            // For API requests (attendance, api, auth/api, etc.), return 401 instead of redirecting
                            // CORS headers will be added by the global CORS configuration
                            if (path.startsWith("/attendance/") || 
                                path.startsWith("/api/") || 
                                path.startsWith("/auth/api/") ||
                                path.startsWith("/user/") ||
                                path.startsWith("/oauth2/status")) {
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                // Don't manually add CORS headers - let the global CORS configuration handle it
                                return exchange.getResponse().setComplete();
                            }
                            
                            // For other requests (HTML pages), redirect to login
                            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                            exchange.getResponse().getHeaders().set("Location", "/oauth2/authorization/gateway-client");
                            return exchange.getResponse().setComplete();
                        })
                )
                // ✅ Enable OAuth2 client BEFORE .build()
                .oauth2Client(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((webFilterExchange, authentication) -> {
                            ServerWebExchange exchange = webFilterExchange.getExchange();
                            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                            exchange.getResponse().getHeaders().set("Location", "/");
                            return exchange.getResponse().setComplete();
                        })
                )
                .build(); // 🚀 Build LAST
    }


    @Bean
    public ServerAuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (webFilterExchange, authentication) -> {
            ServerWebExchange exchange = webFilterExchange.getExchange();

            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

            // Store OAuth2User in session for GatewayUserHeaderFilter to access
            return exchange.getSession()
                    .doOnNext(session -> {
                        log.debug("Storing OAuth2User in session for path: {}", exchange.getRequest().getPath().value());
                        session.getAttributes().put("OAUTH2_USER", oauth2User);
                        log.debug("OAuth2User attributes: {}", oauth2User.getAttributes().toString());
                        Object schoolId = oauth2User.getAttribute("school_id");
                        log.debug("OAuth2User school_id: {}", schoolId != null ? schoolId.toString() : "null");
                        // Ensure session is saved
                        session.start();
                    })
                    .then(Mono.fromRunnable(() -> {
                        // Extract user info
                        String userId = oauth2User.getAttribute("sub");
                        if (userId == null) {
                            userId = oauth2User.getName();
                        }

                        // Set cookies for the UI
                        exchange.getResponse().addCookie(
                                ResponseCookie.from("user_id", userId != null ? userId : "")
                                        .httpOnly(false)
                                        .secure(false)
                                        .sameSite("Lax")  // important for cross-site redirect
                                        .maxAge(Duration.ofHours(1))
                                        .path("/")
                                        .build()
                        );

                        exchange.getResponse().addCookie(
                                ResponseCookie.from("authenticated", "true")
                                        .httpOnly(false)
                                        .secure(false)
                                        .sameSite("Lax")
                                        .maxAge(Duration.ofHours(1))
                                        .path("/")
                                        .build()
                        );

                        // ✅ Redirect to your Nuxt UI
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().set("Location", "http://localhost:3000");
                    }))
                    .then(exchange.getResponse().setComplete());
        };
    }

    @Bean
    public ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }


    @Bean
    public ReactiveOAuth2AuthorizedClientService authorizedClientService(ReactiveClientRegistrationRepository clients) {
        return new InMemoryReactiveOAuth2AuthorizedClientService(clients);
    }

    /**
     * CORS filter for gateway controller endpoints (e.g., /user/me, /oauth2/status)
     * This is needed because globalcors in application.yml only applies to routed endpoints,
     * not to gateway's own controller endpoints.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "https://digikool.com",
                "https://www.digikool.com"
        ));
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}