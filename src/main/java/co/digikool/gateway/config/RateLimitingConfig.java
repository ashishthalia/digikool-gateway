package co.digikool.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitingConfig {
    
    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter() {
        // Default rate limiter - can be overridden per route
        return new RedisRateLimiter(10, 20); // 10 requests per second, burst capacity of 20
    }
    
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        // Rate limit by user ID extracted from token
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            // Fallback to IP address if no user ID
            return Mono.just("ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        };
    }
    
    @Bean
    public KeyResolver ipKeyResolver() {
        // Rate limit by IP address
        return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }
    
    @Bean
    public KeyResolver pathKeyResolver() {
        // Rate limit by path
        return exchange -> Mono.just(exchange.getRequest().getURI().getPath());
    }
}
